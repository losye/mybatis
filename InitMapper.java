#初始化工作

MapperRegistry.addMapper  把所有Mapper接口(如：UserMapper.java )类注册到

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

  public <T> void addMapper(Class<T> type) {
    //mapper必须是接口！才会添加
    if (type.isInterface()) {
      if (hasMapper(type)) {
        //如果重复添加了，报错
        throw new BindingException("Type " + type + " is already known to the MapperRegistry.");
      }
      boolean loadCompleted = false;
      try {
        knownMappers.put(type, new MapperProxyFactory<T>(type));
        // It's important that the type is added before the parser is run
        // otherwise the binding may automatically be attempted by the
        // mapper parser. If the type is already known, it won't try.
        MapperAnnotationBuilder parser = new MapperAnnotationBuilder(config, type);
        //解析annotation的sql语句
        parser.parse();
        loadCompleted = true;
      } finally {
        //如果加载过程中出现异常需要再将这个mapper从mybatis中删除,这种方式比较丑陋吧，难道是不得已而为之？
        if (!loadCompleted) {
          knownMappers.remove(type);
        }
      }
    }
  }
  
  public void parse() {
    String resource = type.toString();
    if (!configuration.isResourceLoaded(resource)) {
      loadXmlResource();
      configuration.addLoadedResource(resource);
      assistant.setCurrentNamespace(type.getName());
      parseCache();
      parseCacheRef();
      //e.g. 得到 UserMapper 这个接口的所有方法
      Method[] methods = type.getMethods();
      for (Method method : methods) {
        try {
          // issue #237
          if (!method.isBridge()) {
            //见@1 注：这个仅仅解析annotation的sql
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
  
  //@1  把对应方法的方法名、 解析后的sql 、等等信息放入assistant 
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
