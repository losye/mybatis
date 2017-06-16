#初始化工作
-stack
org.apache.ibatis.binding.MapperRegistry.addMapper(Class<T> type)
    org.apache.ibatis.builder.annotation.MapperAnnotationBuilder.parse()
    org.apache.ibatis.builder.annotation.MapperAnnotationBuilder.loadXmlResource()
    org.apache.ibatis.builder.annotation.MapperAnnotationBuilder.parsePendingMethods()

 1、把所有Mapper接口(如：UserMapper.java )类注册到

 private final Map<Class<?>, MapperProxyFactory<?>> knownMappers = new HashMap<Class<?>, MapperProxyFactory<?>>() 中
 
 可以看到 value 放的是Mapper代理的工厂类 如下
 
 public class MapperProxyFactory<T> {
  private final Class<T> mapperInterface;
  private Map<Method, MapperMethod> methodCache = new ConcurrentHashMap<Method, MapperMethod>();
  
  protected T newInstance(MapperProxy<T> mapperProxy) {
    //用JDK自带的动态代理生成映射器
    return (T) Proxy.newProxyInstance(mapperInterface.getClassLoader(), new Class[] { mapperInterface }, mapperProxy);
  }

  public T newInstance(SqlSession sqlSession) {
    final MapperProxy<T> mapperProxy = new MapperProxy<T>(sqlSession, mapperInterface, methodCache);
    return newInstance(mapperProxy);
  }
 }

首先 调用 MapperRegistry.addMapper 
  public <T> void addMapper(Class<T> type) {
    //mapper必须是接口！才会添加
    if (type.isInterface()) {
      if (hasMapper(type)) {
        //如果重复添加了，报错
        throw new BindingException("Type " + type + " is already known to the MapperRegistry.");
      }
      boolean loadCompleted = false;
      try {
      //如上所说 将Mapper接口 注册到map中
        knownMappers.put(type, new MapperProxyFactory<T>(type));
        // It's important that the type is added before the parser is run
        // otherwise the binding may automatically be attempted by the
        // mapper parser. If the type is already known, it won't try.
        MapperAnnotationBuilder parser = new MapperAnnotationBuilder(config, type);
        //解析annotation的sql语句  见下方解析
        parser.parse();
        loadCompleted = true;
      } finally {
        if (!loadCompleted) {
          knownMappers.remove(type);
        }
      }
    }
  }
  //解析Mapper
  public void parse() {
    String resource = type.toString();
    if (!configuration.isResourceLoaded(resource)) {
      //根据Mapper接口 解析对应名称的xml文件 具体见下方代码
      loadXmlResource();
      configuration.addLoadedResource(resource);
      assistant.setCurrentNamespace(type.getName());
      parseCache();
      parseCacheRef();
      // 得到 Mapper接口的所有方法
      Method[] methods = type.getMethods();
      for (Method method : methods) {
        try {
          // issue #237
          if (!method.isBridge()) {
            //见@@1 注：这个仅仅解析annotation的sql
           parseStatement(method);
          }
        } catch (IncompleteElementException e) {
          configuration.addIncompleteMethod(new MethodResolver(this, method));
        }
      }
    }
    parsePendingMethods();
  }
  
    //@@1  把对应方法的方法名、 解析后的sql 、等等信息放入configuration
  assistant.addMappedStatement(
          mappedStatementId,
          sqlSource,
          statementType,
          sqlCommandType,
          fetchSize,
          timeout,
          // ParameterMapID
          null,
          parameterTypeClass,
          resultMapId,
          getReturnType(method),
          resultSetType,
          flushCache,
          useCache,
          // TODO issue #577
          false,
          keyGenerator,
          keyProperty,
          keyColumn,
          // DatabaseID
          null,
          languageDriver,
          // ResultSets
          null);
    }


// 类似UserMapper  -> 则去找 nameSpace + Class.getType().getName()(package) + UserMapper.xml
 private void loadXmlResource() {
    // Spring may not know the real resource name so we check a flag
    // to prevent loading again a resource twice
    // this flag is set at XMLMapperBuilder#bindMapperForNamespace
    if (!configuration.isResourceLoaded("namespace:" + type.getName())) {
      String xmlResource = type.getName().replace('.', '/') + ".xml";
      InputStream inputStream = null;
      try {
        inputStream = Resources.getResourceAsStream(type.getClassLoader(), xmlResource);
      } catch (IOException e) {
        // ignore, resource is not required
      }
      if (inputStream != null) {
        XMLMapperBuilder xmlParser = new XMLMapperBuilder(inputStream, assistant.getConfiguration(), xmlResource, configuration.getSqlFragments(), type.getName());
        xmlParser.parse();
      }
    }
  }

 
 -解析xml的stack
 org.apache.ibatis.builder.xml.MapperAnnotationBuilder.loadXmlResource() 
  org.apache.ibatis.builder.xml.XMLMapperBuilder.parse();
   org.apache.ibatis.builder.xml.XMLMapperBuilder.configurationElement(parser.evalNode("/mapper"));//得到Mapper.xml 中的<mapper> </mapper> 节点
    org.apache.ibatis.builder.xml.XMLMapperBuilder.resultMapElements(List<XNode> list) //配置resultMap
     org.apache.ibatis.builder.xml.XMLMapperBuilder.sqlElement(List<XNode> list) //配置sql(定义可重用的 SQL 代码段)
      org.apache.ibatis.builder.xml.XMLMapperBuilder.buildStatementFromContext(List<XNode> list) //配置select|insert|update|delete
       org.apache.ibatis.builder.xml.XMLStatementBuilder.parseStatementNode() //解析语句(select|insert|update|delete)

//配置mapper元素
//	<mapper namespace="org.mybatis.example.BlogMapper">
//	  <select id="selectBlog" parameterType="int" resultType="Blog">
//	    select * from Blog where id = #{id}
//	  </select>
//	</mapper>
  这个context就是整个<mapper></mapper>节点（包括子节点）
  private void configurationElement(XNode context) {
    try {
      //1.配置namespace
      String namespace = context.getStringAttribute("namespace");
      if (namespace.equals("")) {
        throw new BuilderException("Mapper's namespace cannot be empty");
      }
      builderAssistant.setCurrentNamespace(namespace);
      //2.配置cache-ref
      cacheRefElement(context.evalNode("cache-ref"));
      //3.配置cache
      cacheElement(context.evalNode("cache"));
      //4.配置parameterMap(已经废弃,老式风格的参数映射)
      parameterMapElement(context.evalNodes("/mapper/parameterMap"));
      //5.配置resultMap(高级功能)
      resultMapElements(context.evalNodes("/mapper/resultMap"));
      //6.配置sql(定义可重用的 SQL 代码段)
      sqlElement(context.evalNodes("/mapper/sql"));
      //7.配置select|insert|update|delete TODO
      buildStatementFromContext(context.evalNodes("select|insert|update|delete"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing Mapper XML. Cause: " + e, e);
    }
  }

  5和6类似  我们以5为例
  -stack
  org.apache.ibatis.builder.xml.resultMapElements(List<XNode> list)
    org.apache.ibatis.builder.xml.resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings)
      org.apache.ibatis.builder.ResultMapResolver.resolve()
	  org.apache.ibatis.builder.MapperBuilderAssistant.addResultMap
	    org.apache.ibatis.session.Configuration.addResultMap(ResultMap rm)
	  
	  根据栈可以观察到最终也是将解析xml的信息放入Configuration中, 具体各个类如下：
	  //5.配置resultMap,高级功能
  private void resultMapElements(List<XNode> list) throws Exception {
      //基本上就是循环把resultMap加入到Configuration里去,保持2份，一份缩略，一分全名
    for (XNode resultMapNode : list) {
      try {
          //循环调resultMapElement
        resultMapElement(resultMapNode);
      } catch (IncompleteElementException e) {
        // ignore, it will be retried
      }
    }
  }
  
//5.1 配置resultMap
  private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings) throws Exception {
    //错误上下文
//取得标示符   ("resultMap[userResultMap]")
//    <resultMap id="userResultMap" type="User">
//      <id property="id" column="user_id" />
//      <result property="username" column="username"/>
//      <result property="password" column="password"/>
//    </resultMap>
    ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier());
    String id = resultMapNode.getStringAttribute("id",
        resultMapNode.getValueBasedIdentifier());
    //一般拿type就可以了，后面3个难道是兼容老的代码？
    String type = resultMapNode.getStringAttribute("type",
        resultMapNode.getStringAttribute("ofType",
            resultMapNode.getStringAttribute("resultType",
                resultMapNode.getStringAttribute("javaType"))));
    //高级功能，还支持继承?
//  <resultMap id="carResult" type="Car" extends="vehicleResult">
//    <result property="doorCount" column="door_count" />
//  </resultMap>
    String extend = resultMapNode.getStringAttribute("extends");
    //autoMapping
    Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");
    Class<?> typeClass = resolveClass(type);
    Discriminator discriminator = null;
    List<ResultMapping> resultMappings = new ArrayList<ResultMapping>();
    resultMappings.addAll(additionalResultMappings);
    List<XNode> resultChildren = resultMapNode.getChildren();
    for (XNode resultChild : resultChildren) {
      if ("constructor".equals(resultChild.getName())) {
        //解析result map的constructor
        processConstructorElement(resultChild, typeClass, resultMappings);
      } else if ("discriminator".equals(resultChild.getName())) {
        //解析result map的discriminator
        discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
      } else {
        List<ResultFlag> flags = new ArrayList<ResultFlag>();
        if ("id".equals(resultChild.getName())) {
          flags.add(ResultFlag.ID);
        }
        //调5.1.1 buildResultMappingFromContext,得到ResultMapping
        resultMappings.add(buildResultMappingFromContext(resultChild, typeClass, flags));
      }
    }
    //最后再调ResultMapResolver得到ResultMap
    ResultMapResolver resultMapResolver = new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator, resultMappings, autoMapping);
    try {
      return resultMapResolver.resolve();
    } catch (IncompleteElementException  e) {
      configuration.addIncompleteResultMap(resultMapResolver);
      throw e;
    }
  }

很明显了解析得到ResultMap节点及子节点并得信息 (id, typeClass, extend, discriminator, resultMappings, autoMapping)后
调用resultMapResolver.resolve()把信息放入Configuration 类中去
 public ResultMap resolve() {
      //解析又去调用MapperBuilderAssistant.addResultMap
    return assistant.addResultMap(this.id, this.type, this.extend, this.discriminator, this.resultMappings, this.autoMapping);
  }
  
//增加ResultMap
  public ResultMap addResultMap(
      String id,
      Class<?> type,
      String extend,
      Discriminator discriminator,
      List<ResultMapping> resultMappings,
      Boolean autoMapping) {
    id = applyCurrentNamespace(id, false);
    extend = applyCurrentNamespace(extend, true);

    //建造者模式
    ResultMap.Builder resultMapBuilder = new ResultMap.Builder(configuration, id, type, resultMappings, autoMapping);
    if (extend != null) {
      if (!configuration.hasResultMap(extend)) {
        throw new IncompleteElementException("Could not find a parent resultmap with id '" + extend + "'");
      }
      ResultMap resultMap = configuration.getResultMap(extend);
      List<ResultMapping> extendedResultMappings = new ArrayList<ResultMapping>(resultMap.getResultMappings());
      extendedResultMappings.removeAll(resultMappings);
      // Remove parent constructor if this resultMap declares a constructor.
      boolean declaresConstructor = false;
      for (ResultMapping resultMapping : resultMappings) {
        if (resultMapping.getFlags().contains(ResultFlag.CONSTRUCTOR)) {
          declaresConstructor = true;
          break;
        }
      }
      if (declaresConstructor) {
        Iterator<ResultMapping> extendedResultMappingsIter = extendedResultMappings.iterator();
        while (extendedResultMappingsIter.hasNext()) {
          if (extendedResultMappingsIter.next().getFlags().contains(ResultFlag.CONSTRUCTOR)) {
            extendedResultMappingsIter.remove();
          }
        }
      }
      resultMappings.addAll(extendedResultMappings);
    }
    resultMapBuilder.discriminator(discriminator);
    ResultMap resultMap = resultMapBuilder.build();
    configuration.addResultMap(resultMap);
    return resultMap;
  }

   public void addResultMap(ResultMap rm) {
    resultMaps.put(rm.getId(), rm);
    checkLocallyForDiscriminatedNestedResultMaps(rm);
    checkGloballyForDiscriminatedNestedResultMaps(rm);
  }


  
	  

  //解析语句(select|insert|update|delete)
//<select
//  id="selectPerson"
//  parameterType="int"
//  parameterMap="deprecated"
//  resultType="hashmap"
//  resultMap="personResultMap"
//  flushCache="false"
//  useCache="true"
//  timeout="10000"
//  fetchSize="256"
//  statementType="PREPARED"
//  resultSetType="FORWARD_ONLY">
//  SELECT * FROM PERSON WHERE ID = #{id}
//</select>
  public void parseStatementNode() {
    String id = context.getStringAttribute("id");
    String databaseId = context.getStringAttribute("databaseId");

    //如果databaseId不匹配，退出
    if (!databaseIdMatchesCurrent(id, databaseId, this.requiredDatabaseId)) {
      return;
    }

    //暗示驱动程序每次批量返回的结果行数
    Integer fetchSize = context.getIntAttribute("fetchSize");
    //超时时间
    Integer timeout = context.getIntAttribute("timeout");
    //引用外部 parameterMap,已废弃
    String parameterMap = context.getStringAttribute("parameterMap");
    //参数类型
    String parameterType = context.getStringAttribute("parameterType");
    Class<?> parameterTypeClass = resolveClass(parameterType);
    //引用外部的 resultMap(高级功能)
    String resultMap = context.getStringAttribute("resultMap");
    //结果类型
    String resultType = context.getStringAttribute("resultType");
    //脚本语言,mybatis3.2的新功能
    String lang = context.getStringAttribute("lang");
    //得到语言驱动
    LanguageDriver langDriver = getLanguageDriver(lang);

    Class<?> resultTypeClass = resolveClass(resultType);
    //结果集类型，FORWARD_ONLY|SCROLL_SENSITIVE|SCROLL_INSENSITIVE 中的一种
    String resultSetType = context.getStringAttribute("resultSetType");
    //语句类型, STATEMENT|PREPARED|CALLABLE 的一种
    StatementType statementType = StatementType.valueOf(context.getStringAttribute("statementType", StatementType.PREPARED.toString()));
    ResultSetType resultSetTypeEnum = resolveResultSetType(resultSetType);

    //获取命令类型(select|insert|update|delete)
    String nodeName = context.getNode().getNodeName();
    SqlCommandType sqlCommandType = SqlCommandType.valueOf(nodeName.toUpperCase(Locale.ENGLISH));
    boolean isSelect = sqlCommandType == SqlCommandType.SELECT;
    boolean flushCache = context.getBooleanAttribute("flushCache", !isSelect);
    //是否要缓存select结果
    boolean useCache = context.getBooleanAttribute("useCache", isSelect);
    //仅针对嵌套结果 select 语句适用：如果为 true，就是假设包含了嵌套结果集或是分组了，这样的话当返回一个主结果行的时候，就不会发生有对前面结果集的引用的情况。
    //这就使得在获取嵌套的结果集的时候不至于导致内存不够用。默认值：false。 
    boolean resultOrdered = context.getBooleanAttribute("resultOrdered", false);

    // Include Fragments before parsing
    //解析之前先解析<include>SQL片段
    XMLIncludeTransformer includeParser = new XMLIncludeTransformer(configuration, builderAssistant);
    includeParser.applyIncludes(context.getNode());

    // Parse selectKey after includes and remove them.
    //解析之前先解析<selectKey>
    processSelectKeyNodes(id, parameterTypeClass, langDriver);
    
    // Parse the SQL (pre: <selectKey> and <include> were parsed and removed)
    //解析成SqlSource，一般是DynamicSqlSource
    SqlSource sqlSource = langDriver.createSqlSource(configuration, context, parameterTypeClass);
    String resultSets = context.getStringAttribute("resultSets");
    //(仅对 insert 有用) 标记一个属性, MyBatis 会通过 getGeneratedKeys 或者通过 insert 语句的 selectKey 子元素设置它的值
    String keyProperty = context.getStringAttribute("keyProperty");
    //(仅对 insert 有用) 标记一个属性, MyBatis 会通过 getGeneratedKeys 或者通过 insert 语句的 selectKey 子元素设置它的值
    String keyColumn = context.getStringAttribute("keyColumn");
    KeyGenerator keyGenerator;
    String keyStatementId = id + SelectKeyGenerator.SELECT_KEY_SUFFIX;
    keyStatementId = builderAssistant.applyCurrentNamespace(keyStatementId, true);
    if (configuration.hasKeyGenerator(keyStatementId)) {
      keyGenerator = configuration.getKeyGenerator(keyStatementId);
    } else {
      keyGenerator = context.getBooleanAttribute("useGeneratedKeys",
          configuration.isUseGeneratedKeys() && SqlCommandType.INSERT.equals(sqlCommandType))
          ? new Jdbc3KeyGenerator() : new NoKeyGenerator();
    }

	
    builderAssistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType,
        fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass,
        resultSetTypeEnum, flushCache, useCache, resultOrdered, 
        keyGenerator, keyProperty, keyColumn, databaseId, langDriver, resultSets);
  }
  

