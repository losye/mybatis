
在上一篇中 [RegistryMapper.java](https://github.com/losye/mybatis/blob/master/RegistryMapper.java)中已经讲过 如何xml片段(XNode) 注册到mapperStateMents中

\<select id="selectAuthorWithInlineParams" &nbsp;  parameterType="int"  &nbsp; resultType="org.apache.ibatis.domain.blog.Author">   <br/>
  &nbsp;&nbsp; select * from author where id = #{id}    <br/>
\</select>   <br/>


![image](https://github.com/losye/mybatis/blob/master/image/mapperStatement.png)

resultType 竟然为空？ 其实都放在ResultMaps中了！  <br/>

### 这里有一个疑问 为什么mappedStatements 中 有的id是namespace + id  ？有的是id ？如下图所示  <br/>


![image](https://github.com/losye/mybatis/blob/master/image/resultMaps.png)


可以看到这个sqlSoure中的sql语句已经用？替换了占位符#{xxx} 并把parameterType 和  resultMaps注册其中 <br/>


![image](https://github.com/losye/mybatis/blob/master/image/sqlSource.png)







