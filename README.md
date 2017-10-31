# RobustForSdk
演示如何在SDK开发中使用美团的Robust进行代码的热更新

>一开始要做SDK的热更新，我的内心是拒绝的-_-。看了大名鼎鼎的Tinker、Sophix、Robust之后，基于SDK的应用场景和时效性，我选择了Robust，以下介绍SDK接入Robust的整个流程~

# 接入流程

## 1.Robust无法直接应用在SDK项目的解决方式

首先参考[Robust接入指南](https://github.com/Meituan-Dianping/Robust/blob/master/README-zh.md)完成配置，这里不再赘述

里面写到Module是需要应用程序的形式
```
    apply plugin: 'com.android.application'
    //制作补丁时将这个打开，auto-patch-plugin紧跟着com.android.application
    //apply plugin: 'auto-patch-plugin'
    apply plugin: 'robust'
```
很明显SDK的开发是`apply plugin: 'com.android.library'`，如果编译的话会报类似如下错误
>Failed to notify project evaluation listener.
 Transforms with scopes '[SUB_PROJECTS, SUB_PROJECTS_LOCAL_DEPS, EXTERNAL_LIBRARIES]' cannot be applied to library projects.
 Could not find property 'applicationVariants' on com.android.build.gradle.LibraryExtension_Decorated@6a5c2d2d.

因此我们需要思考如何能打出Robust处理过的jar包呢？

我们不妨将SDK的Module配置成application形式先，然后打个apk包，可以看到\sdk\build\outputs\apk目录下生成了apk包，

![](/pic/QQ截图20171026151701.png)

既然如此，那么apk在打包过程中编译生产的资源文件、代码文件应该会生成在\sdk\build的某个目录下，因此我们通过*.jar搜索找到了apk对应的代码jar文件，路径为
 \sdk\build\intermediates\transforms\proguard\develop\release\jars\3\1f\main.jar，用查看工具可知Robust已经在编译过程中插入热修复需要的代码

![](/pic/QQ截图20171026152420.png)

然后其它资源文件，清单文件，参考标准的aar解压后的结构可以在\sdk\build下能找到其余对应的路径，这里就不再赘述，参考 sdk 目录下build.gradle 的打包jar包task即可

![](/pic/QQ截图20171026153115.png)

## 2.jar包的处理以及aar的打包

从上面的分析我们得知了jar包和各种资源的路径，因此我们可以介入gradle打包apk的过程，将打包apk过程中生成的各项文件进行处理，然后合并成aar包，输出指定的目录。下面就以多渠道的打包处理，讲解一下如何处理

配置两个渠道
```
    // 配置渠道
    productFlavors {
        // 测试渠道
        develop {}
        // 默认
        normal {}
    }
```

在gradle.properties配置两个变量，用于方便切换SDK的打包模式，宿主Module在开发依赖的时候可以根据变量采取不同依赖方式
```
# Application模式，Robust需要是Application才能插入代码和打补丁
isAppModule=true
# Application模式下开启这个就可以打补丁
isPatchModule=false
```



