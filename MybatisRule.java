这里介绍下Myabtis中的一些规则

<mapper namespace='errorNameSpace'></mapper> 中的namespace 默认会是Mapper接口类的路径+xml ，此代在Regisry.java中有介绍过，代码如下
比如是 Mapper 接口为  com.chargedot.cd.DeviceMapper.java  
 
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
  
  并且如果你所填的namespace  是空的 或者 和上面代码中的xmlResource (com.chargedot.cd.DeviceMapper.xml)不同则会报错  代码如下
  this.currentNamespace = "com.chargedot.cd.DeviceMapper.xml"
  currentNameSpace = "errorNameSpace"
  public void setCurrentNamespace(String currentNamespace) {
    if (currentNamespace == null) {
      throw new BuilderException("The mapper element requires a namespace attribute to be specified.");
    }

    if (this.currentNamespace != null && !this.currentNamespace.equals(currentNamespace)) {
      throw new BuilderException("Wrong namespace. Expected '"
          + this.currentNamespace + "' but found '" + currentNamespace + "'.");
    }

    this.currentNamespace = currentNamespace;
  }
  
  
