#初始化工作

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
    //@@1  把对应方法的方法名、 解析后的sql 、等等信息放入assistant 
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
  

