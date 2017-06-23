插件机制
通常我们在 MybaitsConfig.xml 中配置引入插件,本文就以com.github.pagehelper.PageHelper为例
<plugins>
    <!-- com.github.pagehelper为PageHelper类所在包名 -->
    <plugin interceptor="com.github.pagehelper.PageHelper">
        <!-- 使用下面的方式配置参数，后面会有所有的参数介绍 -->
        <property name="param1" value="value1"/>
    </plugin>
</plugins>

自然会有类(XMLConfigBuilder)去加载 MybaitsConfig.xml 文件
 //解析配置
  private void parseConfiguration(XNode root) {
    try {
      //分步骤解析
      //issue #117 read properties first
      //1.properties
      propertiesElement(root.evalNode("properties"));
      //2.类型别名
      typeAliasesElement(root.evalNode("typeAliases"));
      //3.插件
      pluginElement(root.evalNode("plugins"));
      //4.对象工厂
      objectFactoryElement(root.evalNode("objectFactory"));
      //5.对象包装工厂
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      //6.设置
      settingsElement(root.evalNode("settings"));
      // read it after objectFactory and objectWrapperFactory issue #631
      //7.环境
      environmentsElement(root.evalNode("environments"));
      //8.databaseIdProvider
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      //9.类型处理器
      typeHandlerElement(root.evalNode("typeHandlers"));
      //10.映射器
      mapperElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }
  
    //3.插件
  //MyBatis 允许你在某一点拦截已映射语句执行的调用。默认情况下,MyBatis 允许使用插件来拦截方法调用
//<plugins>
//  <plugin interceptor="org.mybatis.example.ExamplePlugin">
//    <property name="someProperty" value="100"/>
//  </plugin>
//</plugins>  
  private void pluginElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        String interceptor = child.getStringAttribute("interceptor");
        Properties properties = child.getChildrenAsProperties();
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).newInstance();
        interceptorInstance.setProperties(properties);
        //调用InterceptorChain.addInterceptor
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }
  
  public void addInterceptor(Interceptor interceptor) {
    interceptorChain.addInterceptor(interceptor);
  }
  
  解析后把Interceptor放入Configuration中
  



/**
 * 插件,用的代理模式
 *
 */
public class Plugin implements InvocationHandler {

  private Object target;
  //这个拦截器就是插件的实现  常见的PageHelper 就是实现的这个接口 public class PageInterceptor implements Interceptor
  private Interceptor interceptor;
  private Map<Class<?>, Set<Method>> signatureMap;

  private Plugin(Object target, Interceptor interceptor, Map<Class<?>, Set<Method>> signatureMap) {
    this.target = target;
    this.interceptor = interceptor;
    this.signatureMap = signatureMap;
  }

  public static Object wrap(Object target, Interceptor interceptor) {
    //取得签名Map
    Map<Class<?>, Set<Method>> signatureMap = getSignatureMap(interceptor);
    //取得要改变行为的类(ParameterHandler|ResultSetHandler|StatementHandler|Executor)
    Class<?> type = target.getClass();
    //取得接口
    Class<?>[] interfaces = getAllInterfaces(type, signatureMap);
    //产生代理
    if (interfaces.length > 0) {
      return Proxy.newProxyInstance(
          type.getClassLoader(),
          interfaces,
          new Plugin(target, interceptor, signatureMap));
    }
    return target;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      //看看如何拦截
      Set<Method> methods = signatureMap.get(method.getDeclaringClass());
      //看哪些方法需要拦截
      if (methods != null && methods.contains(method)) {
        //调用Interceptor.intercept，也即插入了我们自己的逻辑
        return interceptor.intercept(new Invocation(target, method, args));
      }
      //最后还是执行原来逻辑
      return method.invoke(target, args);
    } catch (Exception e) {
      throw ExceptionUtil.unwrapThrowable(e);
    }
  }

  //取得签名Map
  private static Map<Class<?>, Set<Method>> getSignatureMap(Interceptor interceptor) {
    //取Intercepts注解，例子可参见ExamplePlugin.java
    Intercepts interceptsAnnotation = interceptor.getClass().getAnnotation(Intercepts.class);
    // issue #251
    //必须得有Intercepts注解，没有报错
    if (interceptsAnnotation == null) {
      throw new PluginException("No @Intercepts annotation was found in interceptor " + interceptor.getClass().getName());      
    }
    //value是数组型，Signature的数组
    Signature[] sigs = interceptsAnnotation.value();
    //每个class里有多个Method需要被拦截,所以这么定义
    Map<Class<?>, Set<Method>> signatureMap = new HashMap<Class<?>, Set<Method>>();
    for (Signature sig : sigs) {
      Set<Method> methods = signatureMap.get(sig.type());
      if (methods == null) {
        methods = new HashSet<Method>();
        signatureMap.put(sig.type(), methods);
      }
      try {
        Method method = sig.type().getMethod(sig.method(), sig.args());
        methods.add(method);
      } catch (NoSuchMethodException e) {
        throw new PluginException("Could not find method on " + sig.type() + " named " + sig.method() + ". Cause: " + e, e);
      }
    }
    return signatureMap;
  }

  //取得接口
  private static Class<?>[] getAllInterfaces(Class<?> type, Map<Class<?>, Set<Method>> signatureMap) {
    Set<Class<?>> interfaces = new HashSet<Class<?>>();
    while (type != null) {
      for (Class<?> c : type.getInterfaces()) {
        //貌似只能拦截ParameterHandler|ResultSetHandler|StatementHandler|Executor
        //拦截其他的无效
        //当然我们可以覆盖Plugin.wrap方法，达到拦截其他类的功能
        if (signatureMap.containsKey(c)) {
          interfaces.add(c);
        }
      }
      type = type.getSuperclass();
    }
    return interfaces.toArray(new Class<?>[interfaces.size()]);
  }

}



以下是mybatis进行插件代理的代码，可以看到把所有处理器进行pluginAll方法
其实插件机制就是代理模式 ，生成处理器的子类，子类就拥有了插件的功能

 //创建参数处理器
  public ParameterHandler newParameterHandler(MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql) {
    //创建ParameterHandler
    ParameterHandler parameterHandler = mappedStatement.getLang().createParameterHandler(mappedStatement, parameterObject, boundSql);
    //插件在这里插入
    parameterHandler = (ParameterHandler) interceptorChain.pluginAll(parameterHandler);
    return parameterHandler;
  }

  //创建结果集处理器
  public ResultSetHandler newResultSetHandler(Executor executor, MappedStatement mappedStatement, RowBounds rowBounds, ParameterHandler parameterHandler,
      ResultHandler resultHandler, BoundSql boundSql) {
    //创建DefaultResultSetHandler(稍老一点的版本3.1是创建NestedResultSetHandler或者FastResultSetHandler)
    ResultSetHandler resultSetHandler = new DefaultResultSetHandler(executor, mappedStatement, parameterHandler, resultHandler, boundSql, rowBounds);
    //插件在这里插入
    resultSetHandler = (ResultSetHandler) interceptorChain.pluginAll(resultSetHandler);
    return resultSetHandler;
  }

  //创建语句处理器
  public StatementHandler newStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
    //创建路由选择语句处理器
    StatementHandler statementHandler = new RoutingStatementHandler(executor, mappedStatement, parameterObject, rowBounds, resultHandler, boundSql);
    //插件在这里插入
    statementHandler = (StatementHandler) interceptorChain.pluginAll(statementHandler);
    return statementHandler;
  }

 //产生执行器
  public Executor newExecutor(Transaction transaction, ExecutorType executorType) {
    executorType = executorType == null ? defaultExecutorType : executorType;
    //这句再做一下保护,囧,防止粗心大意的人将defaultExecutorType设成null?
    executorType = executorType == null ? ExecutorType.SIMPLE : executorType;
    Executor executor;
    //然后就是简单的3个分支，产生3种执行器BatchExecutor/ReuseExecutor/SimpleExecutor
    if (ExecutorType.BATCH == executorType) {
      executor = new BatchExecutor(this, transaction);
    } else if (ExecutorType.REUSE == executorType) {
      executor = new ReuseExecutor(this, transaction);
    } else {
      executor = new SimpleExecutor(this, transaction);
    }
    //如果要求缓存，生成另一种CachingExecutor(默认就是有缓存),装饰者模式,所以默认都是返回CachingExecutor
    if (cacheEnabled) {
      executor = new CachingExecutor(executor);
    }
    //此处调用插件,通过插件可以改变Executor行为
    executor = (Executor) interceptorChain.pluginAll(executor);
    return executor;
  }

  public Object pluginAll(Object target) {
    //循环调用每个Interceptor.plugin方法
    for (Interceptor interceptor : interceptors) {
      target = interceptor.plugin(target);
    }
    return target;
  }



以下是PageHelper插件源码，  通常我们使用插件PageHelper.startPage方法
我们看看分页插件是如何做的
@SuppressWarnings({"rawtypes", "unchecked"})
@Intercepts(
    {
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
    }
)
public class PageInterceptor implements Interceptor {
    //缓存count查询的ms
    protected Cache<CacheKey, MappedStatement> msCountMap = null;
    private Dialect dialect;
    private String default_dialect_class = "com.github.pagehelper.PageHelper";
    private Field additionalParametersField;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        try {
            Object[] args = invocation.getArgs();
            MappedStatement ms = (MappedStatement) args[0];
            Object parameter = args[1];
            RowBounds rowBounds = (RowBounds) args[2];
            ResultHandler resultHandler = (ResultHandler) args[3];
            Executor executor = (Executor) invocation.getTarget();
            CacheKey cacheKey;
            BoundSql boundSql;
            //由于逻辑关系，只会进入一次
            if(args.length == 4){
                //4 个参数时
                boundSql = ms.getBoundSql(parameter);
                cacheKey = executor.createCacheKey(ms, parameter, rowBounds, boundSql);
            } else {
                //6 个参数时
                cacheKey = (CacheKey) args[4];
                boundSql = (BoundSql) args[5];
            }
            List resultList;
            //调用方法判断是否需要进行分页，如果不需要，直接返回结果
            if (!dialect.skip(ms, parameter, rowBounds)) {
                //反射获取动态参数
                Map<String, Object> additionalParameters = (Map<String, Object>) additionalParametersField.get(boundSql);
                //判断是否需要进行 count 查询
                if (dialect.beforeCount(ms, parameter, rowBounds)) {
                    //创建 count 查询的缓存 key
                    CacheKey countKey = executor.createCacheKey(ms, parameter, RowBounds.DEFAULT, boundSql);
                    countKey.update(MSUtils.COUNT);
                    MappedStatement countMs = msCountMap.get(countKey);
                    if (countMs == null) {
                        //根据当前的 ms 创建一个返回值为 Long 类型的 ms
                        countMs = MSUtils.newCountMappedStatement(ms);
                        msCountMap.put(countKey, countMs);
                    }
                    //调用方言获取 count sql
                    String countSql = dialect.getCountSql(ms, boundSql, parameter, rowBounds, countKey);
                    countKey.update(countSql);
                    BoundSql countBoundSql = new BoundSql(ms.getConfiguration(), countSql, boundSql.getParameterMappings(), parameter);
                    //当使用动态 SQL 时，可能会产生临时的参数，这些参数需要手动设置到新的 BoundSql 中
                    for (String key : additionalParameters.keySet()) {
                        countBoundSql.setAdditionalParameter(key, additionalParameters.get(key));
                    }
                    //执行 count 查询
                    Object countResultList = executor.query(countMs, parameter, RowBounds.DEFAULT, resultHandler, countKey, countBoundSql);
                    Long count = (Long) ((List) countResultList).get(0);
                    //处理查询总数
                    //返回 true 时继续分页查询，false 时直接返回
                    if (!dialect.afterCount(count, parameter, rowBounds)) {
                        //当查询总数为 0 时，直接返回空的结果
                        return dialect.afterPage(new ArrayList(), parameter, rowBounds);
                    }
                }
                //判断是否需要进行分页查询
                if (dialect.beforePage(ms, parameter, rowBounds)) {
                    //生成分页的缓存 key
                    CacheKey pageKey = cacheKey;
                    //处理参数对象
                    parameter = dialect.processParameterObject(ms, parameter, boundSql, pageKey);
                    //调用方言获取分页 sql
                    String pageSql = dialect.getPageSql(ms, boundSql, parameter, rowBounds, pageKey);
                    BoundSql pageBoundSql = new BoundSql(ms.getConfiguration(), pageSql, boundSql.getParameterMappings(), parameter);
                    //设置动态参数
                    for (String key : additionalParameters.keySet()) {
                        pageBoundSql.setAdditionalParameter(key, additionalParameters.get(key));
                    }
                    //执行分页查询
                    resultList = executor.query(ms, parameter, RowBounds.DEFAULT, resultHandler, pageKey, pageBoundSql);
                } else {
                    //不执行分页的情况下，也不执行内存分页
                    resultList = executor.query(ms, parameter, RowBounds.DEFAULT, resultHandler, cacheKey, boundSql);
                }
            } else {
                //rowBounds用参数值，不使用分页插件处理时，仍然支持默认的内存分页
                resultList = executor.query(ms, parameter, rowBounds, resultHandler, cacheKey, boundSql);
            }
            return dialect.afterPage(resultList, parameter, rowBounds);
        } finally {
            dialect.afterAll();
        }
    }

    @Override
    public Object plugin(Object target) {
        //TODO Spring bean 方式配置时，如果没有配置属性就不会执行下面的 setProperties 方法，就不会初始化，因此考虑在这个方法中做一次判断和初始化
        //TODO https://github.com/pagehelper/Mybatis-PageHelper/issues/26
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        //缓存 count ms
        msCountMap = CacheFactory.createCache(properties.getProperty("msCountCache"), "ms", properties);
        String dialectClass = properties.getProperty("dialect");
        if (StringUtil.isEmpty(dialectClass)) {
            dialectClass = default_dialect_class;
        }
        try {
            Class<?> aClass = Class.forName(dialectClass);
            dialect = (Dialect) aClass.newInstance();
        } catch (Exception e) {
            throw new PageException(e);
        }
        dialect.setProperties(properties);
        try {
            //反射获取 BoundSql 中的 additionalParameters 属性
            additionalParametersField = BoundSql.class.getDeclaredField("additionalParameters");
            additionalParametersField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new PageException(e);
        }
    }

}


