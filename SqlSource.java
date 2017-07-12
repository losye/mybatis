

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

public class SqlSourceBuilder extends BaseBuilder {
  
   public SqlSource parse(String originalSql, Class<?> parameterType, Map<String, Object> additionalParameters) {
    ParameterMappingTokenHandler handler = new ParameterMappingTokenHandler(configuration, parameterType, additionalParameters);
    //替换#{}中间的部分,如何替换，逻辑在ParameterMappingTokenHandler
    GenericTokenParser parser = new GenericTokenParser("#{", "}", handler);
    String sql = parser.parse(originalSql);
    //返回静态SQL源码
    return new StaticSqlSource(configuration, sql, handler.getParameterMappings());
  }
  
  private static class ParameterMappingTokenHandler extends BaseBuilder implements TokenHandler {
      @Override
    public String handleToken(String content) {
      //先构建参数映射
      parameterMappings.add(buildParameterMapping(content));
      //如何替换很简单，永远是一个问号，但是参数的信息要记录在parameterMappings里面供后续使用
      return "?";
    }
    private ParameterMapping buildParameterMapping(String content) {
        //#{favouriteSection,jdbcType=VARCHAR}
        //先解析参数映射,就是转化成一个hashmap
      Map<String, String> propertiesMap = parseParameterMapping(content);
      String property = propertiesMap.get("property");
      Class<?> propertyType;
      //这里分支比较多，需要逐个理解
      if (metaParameters.hasGetter(property)) { // issue #448 get type from additional params
        propertyType = metaParameters.getGetterType(property);
      } else if (typeHandlerRegistry.hasTypeHandler(parameterType)) {
        propertyType = parameterType;
      } else if (JdbcType.CURSOR.name().equals(propertiesMap.get("jdbcType"))) {
        propertyType = java.sql.ResultSet.class;
      } else if (property != null) {
        MetaClass metaClass = MetaClass.forClass(parameterType);
        if (metaClass.hasGetter(property)) {
          propertyType = metaClass.getGetterType(property);
        } else {
          propertyType = Object.class;
        }
      } else {
        propertyType = Object.class;
      }
      ParameterMapping.Builder builder = new ParameterMapping.Builder(configuration, property, propertyType);
      Class<?> javaType = propertyType;
      String typeHandlerAlias = null;
      for (Map.Entry<String, String> entry : propertiesMap.entrySet()) {
        String name = entry.getKey();
        String value = entry.getValue();
        if ("javaType".equals(name)) {
          javaType = resolveClass(value);
          builder.javaType(javaType);
        } else if ("jdbcType".equals(name)) {
          builder.jdbcType(resolveJdbcType(value));
        } else if ("mode".equals(name)) {
          builder.mode(resolveParameterMode(value));
        } else if ("numericScale".equals(name)) {
          builder.numericScale(Integer.valueOf(value));
        } else if ("resultMap".equals(name)) {
          builder.resultMapId(value);
        } else if ("typeHandler".equals(name)) {
          typeHandlerAlias = value;
        } else if ("jdbcTypeName".equals(name)) {
          builder.jdbcTypeName(value);
        } else if ("property".equals(name)) {
          // Do Nothing
        } else if ("expression".equals(name)) {
          throw new BuilderException("Expression based parameters are not supported yet");
        } else {
          throw new BuilderException("An invalid property '" + name + "' was found in mapping #{" + content + "}.  Valid properties are " + parameterProperties);
        }
      }
      //#{age,javaType=int,jdbcType=NUMERIC,typeHandler=MyTypeHandler}
      if (typeHandlerAlias != null) {
        builder.typeHandler(resolveTypeHandler(javaType, typeHandlerAlias));
      }
      return builder.build();
    }
    
  }

}




