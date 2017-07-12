

public class DefaultParameterHandler implements ParameterHandler {

  private final TypeHandlerRegistry typeHandlerRegistry;

  private final MappedStatement mappedStatement;
  private final Object parameterObject;
  private BoundSql boundSql;
  private Configuration configuration;

  public DefaultParameterHandler(MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql) {
    this.mappedStatement = mappedStatement;
    this.configuration = mappedStatement.getConfiguration();
    this.typeHandlerRegistry = mappedStatement.getConfiguration().getTypeHandlerRegistry();
    this.parameterObject = parameterObject;
    this.boundSql = boundSql;
  }

  @Override
  public Object getParameterObject() {
    return parameterObject;
  }

  //设置参数
  @Override
  public void setParameters(PreparedStatement ps) throws SQLException {
    ErrorContext.instance().activity("setting parameters").object(mappedStatement.getParameterMap().getId());
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    
    /**
     * 当参数为 ${value} 时， parameterMappings为空，此时originSql已经拼接完成，如 select * from table where id = 3 OR 1 = 1
     *
     * 当参数为 #{value} 时， parameterMappings不为空，#{value} 会被？所代替，如 select * from table where id = ？
     * 此时会有ParameterMappings，value -> xxx，找到合适的TypeHandler塞入PrepareStatement中。
     */
    if (parameterMappings != null) {
      //循环设参数
      for (int i = 0; i < parameterMappings.size(); i++) {
        ParameterMapping parameterMapping = parameterMappings.get(i);
        if (parameterMapping.getMode() != ParameterMode.OUT) {
          //如果不是OUT，才设进去
          Object value;
          String propertyName = parameterMapping.getProperty();
          if (boundSql.hasAdditionalParameter(propertyName)) { // issue #448 ask first for additional params
            //若有额外的参数, 设为额外的参数
            value = boundSql.getAdditionalParameter(propertyName);
          } else if (parameterObject == null) {
            //若参数为null，直接设null
            value = null;
          } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
            //若参数有相应的TypeHandler，直接设object
            value = parameterObject;
          } else {
            //除此以外，MetaObject.getValue反射取得值设进去
            MetaObject metaObject = configuration.newMetaObject(parameterObject);
            value = metaObject.getValue(propertyName);
          }
          TypeHandler typeHandler = parameterMapping.getTypeHandler();
          JdbcType jdbcType = parameterMapping.getJdbcType();
          if (value == null && jdbcType == null) {
            //不同类型的set方法不同，所以委派给子类的setParameter方法
            jdbcType = configuration.getJdbcTypeForNull();
          }
          typeHandler.setParameter(ps, i + 1, value, jdbcType);
        }
      }
    }
  }

}
