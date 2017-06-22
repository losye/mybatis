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
