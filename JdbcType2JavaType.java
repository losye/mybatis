JdbcType2JavaType 在mybatis中的转换

public interface TypeHandler<T> {

  //设置参数
  void setParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException;

  //取得结果,供普通select用
  T getResult(ResultSet rs, String columnName) throws SQLException;

  //取得结果,供普通select用
  T getResult(ResultSet rs, int columnIndex) throws SQLException;

  //取得结果,供SP用
  T getResult(CallableStatement cs, int columnIndex) throws SQLException;

}



public class StringTypeHandler extends BaseTypeHandler<String> {

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
      throws SQLException {
    ps.setString(i, parameter);
  }

  @Override
  public String getNullableResult(ResultSet rs, String columnName)
      throws SQLException {
    return rs.getString(columnName);
  }

  @Override
  public String getNullableResult(ResultSet rs, int columnIndex)
      throws SQLException {
    return rs.getString(columnIndex);
  }

  @Override
  public String getNullableResult(CallableStatement cs, int columnIndex)
      throws SQLException {
    return cs.getString(columnIndex);
  }
}


