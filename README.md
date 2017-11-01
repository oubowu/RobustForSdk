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

我们不妨将SDK的Module配置成application形式先，然后打个develop渠道的apk包，可以看到\sdk\build\outputs\apk\develop\release目录下生成了apk包，

![](/pic/QQ截图20171026151701.png)

既然如此，那么apk在打包过程中编译生产的资源文件、代码文件应该会生成在\sdk\build的某个目录下，因此我们通过*.jar搜索找到了apk对应的代码jar文件，gradle-3.0.0 & robust-0.4.71对应的路径为
\sdk\build\intermediates\transforms\proguard\develop\release\0.jar；gradle-2.3.3 & robust-0.4.7对应的路径为 \sdk\build\intermediates\transforms\proguard\develop\release\jars\3\1f\main.jar，用查看工具可知Robust已经在编译过程中插入热修复需要的代码

![](/pic/QQ截图20171026152420.png)

然后其它资源文件，清单文件，参考标准的aar解压后的结构可以在\sdk\build下能找到其余对应的路径，这里就不再赘述，参考 sdk 目录下build.gradle 的打包jar包task即可

![](/pic/QQ截图20171026153115.png)

## 2.jar包的处理以及aar的打包

从上面的分析我们得知了jar包和各种资源的路径，因此我们可以介入gradle打包apk的过程，将打包apk过程中生成的各项文件进行处理，然后合并成aar包，输出指定的目录。下面就以多渠道的打包处理，讲解一下如何处理

在gradle.properties配置两个变量，用于方便切换SDK的打包模式，宿主Module在开发依赖的时候可以根据变量采取不同依赖方式
```
# Application模式，Robust需要是Application才能插入代码和打补丁
isAppModule=true
# Application模式下开启这个就可以打补丁
isPatchModule=false
```

apply plugin的配置
```
// apply plugin表示该项目会使用指定的插件,sdk对应的是com.android.library
if (isAppModule.toBoolean()) {
    # Application模式，使用robust
    apply plugin: 'com.android.application'
    if (isPatchModule.toBoolean()) {
        //制作补丁时将这个打开，auto-patch-plugin紧跟着com.android.application
        apply plugin: 'auto-patch-plugin'
    }
    apply plugin: 'robust'
} else {
    apply plugin: 'com.android.library'
}
```

配置两个渠道
```
    // 配置渠道
    productFlavors {
        // 测试渠道
        develop {
            dimension "test"
        }
        // 默认
        normal {
            dimension "test"
        }
    }
```

配置清单文件指定的路径，因为application和library的清单文件是有明显区别的
```
    sourceSets {
        main {
            // 指定jni的文件源为文件夹libs
            jniLibs.srcDirs = ['libs']

            // Application和Library清单文件处理方式不同
            if (isAppModule.toBoolean()) {
                manifest.srcFile 'src/main/debug/AndroidManifest.xml'
            } else {
                manifest.srcFile 'src/main/release/AndroidManifest.xml'
            }

        }
    }
```

依赖的配置
```
dependencies {

    // 避免宿主与我们sdk造成第三方jar包冲突，本地依赖第三方jar包不打入sdk模块的jar包
    compileOnly fileTree(dir: 'libs', include: ['*.jar'])

    androidTestImplementation('com.android.support.test.espresso:espresso-core:2.2.2', {
        exclude group: 'com.android.support', module: 'support-annotations'
    })
    testImplementation 'junit:junit:4.12'

    // 制作补丁的时候robust打入jar包里面，不用宿主再去compile，这样sdk的热修复对于宿主无感知
    implementation 'com.meituan.robust:robust:0.4.71'

    // 远程依赖aar包的话不支持provided或者compileOnly形式，由此需要在自定义task打jar包的时候过滤掉
    implementation 'com.orhanobut:logger:2.1.1'

    // 与依赖logger同理
    implementation 'cn.bmob.android:bmob-sdk:3.5.8'

    // secret.jar由secret模块输出的jar包，直接打入sdk模块的jar包，使用api而不是implementation是因为在app模块有直接使用secret里面的方法，因此暴露此依赖
    api files('libs/secret.jar')

}
```

自定义task进行打包的处理，介入项目打release版本apk包的过程
```
// 项目打release版本apk包的话，必然会调用到assemble(渠道)Release的命令，于是我们可以用正则匹配来匹配所有渠道的打Release包过程
Pattern p = Pattern.compile("^assemble(.*)Release\$")

// 在task添加到列表的时候，进行打包task的匹配
tasks.whenTaskAdded { task ->
    if (!isAppModule.toBoolean()) {
        // 不是Application模式不处理
        return
    }
    // 在任务执行的时候，匹配执行assemble(渠道)Release的打APK任务
    Matcher m = p.matcher(task.name)
    if (m.find()) {
        // 取出渠道
        String flavor = m.group(1)
        if (flavor.length() > 1) {
            // 渠道命名的修正，首字母小写，例如develop渠道对应命令为assembleDevelopRelease
            flavor = flavor.substring(0, 1).toLowerCase() + flavor.substring(1)
        }

        // 打release包task完成之后进行资源的整合以及jar包去指定class文件，并且生成aar包
        task.doLast {

            delete {
                // 删除上次生成的文件目录，目录为 \sdk\robustjar\(渠道)\release
                delete projectDir.toString() + File.separator + 'robustjar' + File.separator + flavor + File.separator + "release"
            }

            // 打包所需资源所在的父目录， \sdk\build\intermediates
            String intermediatesPath = buildDir.toString() + File.separator + "intermediates"

            // gradle-3.0.0 & robust-0.4.71对应的路径为 \sdk\build\intermediates\transforms\proguard\(渠道)\release\0.jar
            String robustJarPath = intermediatesPath + File.separator + "transforms" + File.separator + "proguard" + File.separator + flavor + File.separator + "release" + File.separator + "0.jar"

            // gradle-2.3.3 & robust-0.4.7对应的路径为 \sdk\build\intermediates\transforms\proguard\(渠道)\release\jars\3\1f\main.jar
            // String robustJarPath = intermediatesPath + File.separator + "transforms" + File.separator + "proguard" + File.separator + flavor + File.separator + "release" + File.separator + "jars" + File.separator + "3" + File.separator + "1f" + File.separator + "main.jar"

            // 资源文件的路径，\sdk\build\intermediates\assets\(渠道)\release
            String assetsPath = intermediatesPath + File.separator + "assets" + File.separator + flavor + File.separator + "release"

            // 依赖本地jar包路径，\sdk\build\intermediates\jniLibs\(渠道)\release
            String libsPath = intermediatesPath + File.separator + "jniLibs" + File.separator + flavor + File.separator + "release"

            // res资源文件的路径，\sdk\build\intermediates\res\merged\(渠道)\release，经测试发现此目录下生成的.9图片会失效，因此弃置，换另外方式处理
            // String resPath = intermediatesPath + File.separator + "res" + File.separator + "merged" + File.separator + flavor + File.separator + "release"

            // 由于上述问题，直接用项目的res路径 \sdk\src\main\res ，因此第三方依赖的资源文件无法整合，但是我是基于生成只包含自身代码的jar包和资源，其余依赖宿主另外再依赖的方案，所以可以这样处理
            String resPath = projectDir.toString() + File.separator + "src" + File.separator + "main" + File.separator + "res"

            // 资源id路径，\sdk\build\intermediates\symbols\(渠道)\release
            String resIdPath = intermediatesPath + File.separator + "symbols" + File.separator + flavor + File.separator + "release"

            // 清单文件路径，\sdk\build\intermediates\manifests\full\(渠道)\release，由于是生成的application的清单文件，因此下面还会做删除组件声明的处理
            String manifestPath = intermediatesPath + File.separator + "manifests" + File.separator + "full" + File.separator + flavor + File.separator + "release"

            // 整合上述文件后的目标路径，\sdk\robustjar\(渠道)\release\origin
            String destination = projectDir.toString() + File.separator + /*'outputs' + File.separator +*/ 'robustjar' + File.separator + flavor + File.separator + 'release' + File.separator + 'origin'

            // 貌似aidl的文件夹没啥用，打包会根据例如G:\\sms-hotfix\\SmsParsingForRcs-Library\\library\\src\\main\\aidl\\com\\cmic\\IMyAidlInterface.aidl的定义代码生成com.cmic.IMyAidlInterface到jar包里面，因此aidl仅仅是空文件夹
            // String aidlPath = buildDir.toString() + File.separator + "generated" + File.separator + "source" + File.separator + "aidl" + File.separator + flavor + File.separator + "release"

            File file = file(robustJarPath)
            if (file.exists()) {
                println '渠道是：' + flavor + '；开始复制robust插桩jar包'
                copy {

                    // 拷贝到assets目录
                    from(assetsPath) {
                        into 'assets'
                    }

                    //  第三方本地jar包不处理，提供宿主集成的时候另外提供
                    //  from(libsPath) {
                    //      into 'libs'
                    //      include '**/*.jar'
                    //      exclude {
                    //          // println it.path+";"+it.isDirectory()
                    //          it.isDirectory()
                    //      }
                    //  }

                    // .so文件拷贝到jni目录
                    from(libsPath) {
                        into 'jni'
                        include '**/*/*.so'
                    }

                    // 资源文件拷贝到res目录
                    from(resPath) {
                        // 排除MainActivity加载的布局文件，因为输出的是jar包，加MainActivity仅仅是为了能让打apk包任务执行
                        exclude '/layout/activity_main.xml'
                        exclude {
                            // 排除空文件夹
                            it.isDirectory() && it.getFile().listFiles().length == 0
                        }
                        into 'res'
                    }

                    // aidl的文件夹没啥用，不处理
                    // from(aidlPath) {
                    //     into 'aidl'
                    // }

                    // 拷贝此目录下资源id文件 R.txt
                    from resIdPath

                    // 拷贝到目录 \sdk\robustjar\(渠道)\release\origin
                    into destination

                }

                copy {
                    // 复制供宿主的混淆规则，这里我在android{ defaultConfig { consumerProguardFiles 'lib-proguard-rules.pro' }}，配置了一个混淆规则
                    def files = android.defaultConfig.consumerProguardFiles
                    if (files != null && files.size() > 0) {
                        def file1 = files.get(0);
                        //  println '混淆文件路径：'+file1.path
                        from file1.path
                        into destination
                        // 复制混淆规则并且重命名
                        rename(file1.name, 'proguard.txt')
                    }
                }

                // 补丁生成需要的mapping.txt和methodsMap.robust文件
                copy {
                    // 混淆mapping文件的路径，\sdk\build\outputs\mapping\(渠道)\release\mapping.txt
                    from(buildDir.toString() + File.separator + 'outputs' + File.separator + 'mapping' + File.separator + flavor + File.separator + 'release') {
                        include 'mapping.txt'
                    }
                    // 拷贝到目录 \sdk\robustjar\(渠道)\release
                    into projectDir.toString() + File.separator + 'robustjar' + File.separator + flavor + File.separator + 'release'
                }

                copy {
                    // robust生成的methodsMap文件路径，\sdk\build\outputs\robust\methodsMap.robust
                    from(buildDir.toString() + File.separator + 'outputs' + File.separator + 'robust') {
                        include 'methodsMap.robust'
                    }
                    // 拷贝到目录 \sdk\robustjar\(渠道)\release
                    into projectDir.toString() + File.separator + 'robustjar' + File.separator + flavor + File.separator + 'release'
                }

                // 若不存在aidl目录，创建aidl空目录
                createDir(destination + File.separator + "aidl")
                // 同上
                createDir(destination + File.separator + "assets")
                // 同上
                createDir(destination + File.separator + "jni")
                // 同上
                createDir(destination + File.separator + "libs")
                // 同上
                createDir(destination + File.separator + "res")

                // 将清单文件application节点的内容和activity节点的内容替换，例如下面
                <application
                    android:allowBackup="true"
                    tools:replace="android:label"
                    android:label="sdk"
                    android:supportsRtl="true"
                    android:icon="@android:drawable/ic_dialog_info"
                    android:theme="@android:style/Theme.Black"
                    >
                    <activity android:name=".MainActivity">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN"/>
                            <category android:name="android.intent.category.LAUNCHER"/>
                        </intent-filter>
                    </activity>
                </application>
                转换成
                <application
                    android:allowBackup="true"
                    tools:replace="android:label"
                    android:label="sdk"
                    android:supportsRtl="true">
                </application>

                def oldStr = ["<application[\\s\\S]*?>", "<activity[\\s\\S]*?</activity>"]
                def newStr = ["<application\n" + "        android:allowBackup=\"true\"\n" + "        android:supportsRtl=\"true\">", ""]
                // 处理 \sdk\build\intermediates\manifests\full\(渠道)\release\AndroidManifest.xml
                String strBuffer = fileReader(manifestPath + File.separator + "AndroidManifest.xml", oldStr, newStr)
                // 输出至 \sdk\robustjar\(渠道)\release\origin\AndroidManifest.xml
                fileWrite(destination + File.separator + "AndroidManifest.xml", strBuffer)

                println '输出robust插桩jar包成功!'

                // 执行打jar包的task，这里会做原jar包的过滤处理，只保留我们需要的代码
                tasks.findByName('jar_' + flavor).execute()

                // 执行打aar包的task，其实就是将目录\sdk\robustjar\develop\release\origin压缩成aar后缀的压缩包
                tasks.findByName('aar_' + flavor).execute()

            }
        }
    }
}
```

上述task需要执行的另外两个task
```
// 根据渠道生成打jar包和aar包的对应的task
for (String flavor : android.productFlavors.names) {

    // 遍历所有渠道，生成对应渠道打jar包的task，名字为jar_(渠道)
    tasks.create(name: 'jar_' + flavor, type: Jar) {

        println "当前渠道是：" + flavor

        // jar包命名为classes.jar
        baseName 'classes'

        String intermediatesPath = buildDir.toString() + File.separator + "intermediates"

        // gradle-2.3.3 & robust-0.4.7对应的路径为 \sdk\build\intermediates\transforms\proguard\(渠道)\release\jars\3\1f\main.jar
        // String robustJarPath = intermediatesPath + File.separator + "transforms" + File.separator + "proguard" + File.separator + flavor + File.separator + "release" + File.separator + "jars" + File.separator + "3" + File.separator + "1f" + File.separator + "main.jar"

         // gradle-3.0.0 & robust-0.4.71对应的路径为 \sdk\build\intermediates\transforms\proguard\(渠道)\release\0.jar
        String robustJarPath = intermediatesPath + File.separator + "transforms" + File.separator + "proguard" + File.separator + flavor + File.separator + "release" + File.separator + "0.jar"

        def zipFile = new File(robustJarPath)
        // 将jar包解压
        FileTree jarTree = zipTree(zipFile)

        from jarTree

        // jar包输出路径为 \sdk\robustjar\(渠道)\release\origin
        File destDir = file(projectDir.toString() + File.separator + 'robustjar' + File.separator + flavor + File.separator + 'release' + File.separator + 'origin')
        // 设置输出路径
        setDestinationDir destDir

        include {
            // 只打包我们需要的类
            it.path.startsWith('com/oubowu/sdk') || it.path.startsWith('com/meituan/robust') || it.path.startsWith('com/oubowu/secret')
        }

        exclude {
            // println "执行排除：" + it.path
            // 排除R相关class文件，排除MainActivity.class文件
            it.path.startsWith('com/oubowu/sdk/R$') || it.path.startsWith('com/oubowu/sdk/R.class') || it.path.startsWith('com/oubowu/sdk/MainActivity.class')
        }

        println '压缩jar包完毕！！！！！！！！'
    }

    // 遍历所有渠道，生成对应渠道打aar包的task，名字为aar_(渠道)
    tasks.create(name: 'aar_' + flavor, type: Zip) {
        // aar包输出路径为 \sdk\robustjar\(渠道)\release\aar
        File destDir = file(projectDir.toString() + File.separator + 'robustjar' + File.separator + flavor + File.separator + 'release' + File.separator + 'aar')
        // aar包命名为 library-(渠道)-release.aar
        archiveName 'library-' + flavor + '-release.aar'
        // 源路径为 \sdk\robustjar\(渠道)\release\origin
        from projectDir.toString() + File.separator + 'robustjar' + File.separator + flavor + File.separator + 'release' + File.separator + 'origin'
        // 设置压缩后输出的路径
        destinationDir destDir

        println '压缩aar包完毕！！！！！！！！'
    }

}
```

至此我们的自定义打包task就编写完成了，执行assembleDevelopRelease任务，即可生成我们想要的jar包和aar包了

![](/pic/QQ截图20171101111237.png)