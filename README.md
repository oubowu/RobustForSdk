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

# 3.补丁下发和加载的策略
参考官方示例[PatchManipulateImp.java](https://github.com/Meituan-Dianping/Robust/blob/master/app/src/main/java/com/meituan/sample/PatchManipulateImp.java)来定制我们的下发加载策略

![](/pic/补丁策略.png)

如上流程图所述，首先去本地查询是否已经下发过补丁，若有优先加载本地补丁，这种场景是针对没有网络或者请求网络下发补丁失败导致无法修复的情况；然后去请求网络下发补丁的列表信息，sdk版本作为补丁的下发依据，然后补丁版本高的包含低版本的修复代码，我考虑是方便处理，不用加载多个补丁，对应项目发包的策略是一个大版本作为封版建立版本分支，后续此版本上线遇到的bug都基于这个分支做处理，直到下个版本上线合并修复的代码。

具体代码实现如下：
```
        // 创建1个固定线程的线程池，用于串行进行本地补丁的加载和网络请求补丁然后加载的逻辑
        ExecutorService fixedThreadPool = Executors.newFixedThreadPool(1);
        // 读取本地保存的上次加载的补丁的名称
        final String pName = context.getSharedPreferences("com.oubowu.sdk.sp", Context.MODE_PRIVATE).getString("pName", "");
        if (!pName.isEmpty()) {
            // 创建本地补丁加载的线程
            PatchExecutor patchExecutor1 = new PatchExecutor(context.getApplicationContext(), new PatchManipulateImp(true, pName),...);
            // 执行本地补丁加载的线程
            fixedThreadPool.execute(patchExecutor1);
        }

        // 创建网络请求补丁然后加载的线程
        PatchExecutor patchExecutor2 = new PatchExecutor(context.getApplicationContext(), new PatchManipulateImp(false, pName), ...);
        fixedThreadPool.execute(patchExecutor2);
```

补丁的处理，这里我使用Bmob来进行补丁的下发，真正开发的话需要后台实现一个补丁下发接口
```
public class PatchManipulateImp extends PatchManipulate {

    // 是否只做本地补丁的判断
    private boolean mOnlyLocal = true;
    // 保存于本地的补丁的名称
    private String mSavePatchName;

    public PatchManipulateImp(boolean onlyLocal, String savePatchName) {
        mOnlyLocal = onlyLocal;
        mSavePatchName = savePatchName;
    }

    /***
     * connect to the network ,get the latest patches
     * 联网获取最新的补丁
     * @param context
     *
     * @return
     */
    @Override
    protected List<Patch> fetchPatchList(final Context context) {

        final List<Patch> patches = new ArrayList<>();

        if (mOnlyLocal) {
            // 只是做本地判断的话
            if (!mSavePatchName.isEmpty()) {
                // 名称不为空说明存在，添加本地保存的补丁信息，然后返回
                addPatchInfo(context, mSavePatchName, patches);
            }
            return patches;
        }

        // 由于下面做的是异步的网络请求下发补丁，所以使用CountDownLatch进行同步
        final CountDownLatch mCountDownLatch = new CountDownLatch(1);

        //Bmob初始化
        Bmob.initialize(context.getApplicationContext(), "52e558b89195c84cd761afbeabc3df52");

        BmobQuery<com.oubowu.sdk.Patch> query = new BmobQuery<>();
        // 通过sdkVersion查询此sdk版本的线上补丁
        query.addWhereEqualTo("sdkVersion", BuildConfig.VERSION_NAME);
        // 根据patchVersion字段降序显示数据
        query.order("-patchVersion");
        // query.setLimit(1);
        query.findObjects(new FindListener<com.oubowu.sdk.Patch>() {
            @Override
            public void done(List<com.oubowu.sdk.Patch> list, BmobException e) {
                if (e != null) {
                    // 请求补丁列表数据失败
                    Logger.e(e.getMessage());
                    mCountDownLatch.countDown();
                } else {
                    if (list != null && list.size() > 0) {
                        // 取最高补丁版本的补丁
                        final com.oubowu.sdk.Patch p = list.get(0);
                        Logger.e(p.toString());
                        final String filename = p.getPatchUrl().getFilename();
                        // 若sp存的补丁名称跟下发的最高版本的补丁名称不一样的话，下载并应用补丁；或者名称一样，但是本地没有此补丁，下载并应用补丁
                        if (!filename.equals(mSavePatchName) || !(new File(context.getFilesDir(), mSavePatchName).exists())) {
                            File saveFile = new File(context.getFilesDir(), filename);
                            if (!saveFile.exists()) {
                                // 本地没有保存的话，下载补丁
                                p.getPatchUrl().download(saveFile, new DownloadFileListener() {
                                    @Override
                                    public void done(String s, BmobException e) {
                                        if (e != null) {
                                            mCountDownLatch.countDown();
                                        } else {
                                            Logger.e("下载成功，" + s);
                                            context.getSharedPreferences("com.oubowu.sdk.sp", Context.MODE_PRIVATE).edit().putString("pName", filename).apply();
                                            addPatchInfo(context, filename, patches);
                                            mCountDownLatch.countDown();
                                        }
                                    }

                                    @Override
                                    public void onProgress(Integer integer, long l) {
                                    }
                                });
                            } else {
                                // 本地已经保存了的话，保存名称，直接使用
                                context.getSharedPreferences("com.oubowu.sdk.sp", Context.MODE_PRIVATE).edit().putString("pName", filename).apply();
                                addPatchInfo(context, filename, patches);
                                mCountDownLatch.countDown();
                            }
                        }

                    } else {
                        // 此sdk版本没有补丁
                        mCountDownLatch.countDown();
                    }
                }
            }
        });

        try {
            // 阻塞等待网络请求结束
            mCountDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return patches;
    }

    /**
     * 添加补丁信息
     *
     * @param context
     * @param fileName
     * @param patches
     */
    private void addPatchInfo(Context context, String fileName, List<Patch> patches) {
        // 解密下发的已加密补丁并且返回解密后的文件路径，使用ndk保证解密安全性
        String dPatchPath = NdkHelper.p(context, fileName, false);
        File dPatchFile = new File(dPatchPath);
        if (dPatchFile.exists()) {
            // 解密文件存在的话，添加到补丁列表
            Patch patch = new Patch();
            patch.setName(dPatchFile.getName().replace(".jar", ""));
            patch.setLocalPath(dPatchFile.getPath().replace(".jar", ""));
            patch.setPatchesInfoImplClassFullName("com.oubowu.sdk.lib.PatchManipulateImp.PatchesInfoImpl");
            patches.add(patch);
        }
    }

    /**
     * @param context
     * @param patch
     * @return you can verify your patches here
     */
    @Override
    protected boolean verifyPatch(Context context, Patch patch) {
        //do your verification, put the real patch to patch
        //放到app的私有目录
        patch.setTempPath(context.getCacheDir() + File.separator + "robust" + File.separator + patch.getName());
        //in the sample we just copy the file
        try {
            copy(patch.getLocalPath(), patch.getTempPath());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("copy source patch to local patch error, no patch execute in path " + patch.getTempPath());
        }

        // 删除解密的本地补丁
        patch.delete(patch.getLocalPath());

        return true;
    }

    ...

}
```

# 4.补丁的生成和使用
在SdkTest.java我写了一个会抛出NumberFormatException的方法
```
    public static void callBugMethod(Context context) {

        String strFromCPlus = NdkHelper.getStrFromCPlus();

        Logger.e(strFromCPlus);

        int i = Integer.parseInt(strFromCPlus);

    }
```

然后修复callBugMethod方法，添加一个静态内部类
```
    @Modify
    public static void callBugMethod(Context context) {

        String strFromCPlus = NdkHelper.getStrFromCPlus();

        Logger.e(strFromCPlus);

        try {
            int i = Integer.parseInt(strFromCPlus);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            Logger.e("我使用Robust热更新把空指针修复啦！！！");
        }

        MyClass.call();

    }

    @Add
    public static class MyClass {
        public static void call() {
            Logger.e("我使用Robust热更新新增了一个静态内部类");
        }
    }

```

将 \RobustForSdk\gradle.properties isPatchModule设为true，将\sdk\robustjar\develop\release文件夹下的mapping.txt以及methodsMap.robust放到\sdk\robust文件夹
```
    # Application模式，Robust需要是Application才能插入代码和打补丁
    isAppModule=true
    # Application模式下开启这个就可以打补丁
    isPatchModule=true
```

执行assembleDevelopRelease，得到以下提示，即说明生成补丁成功
```
* What went wrong:
Execution failed for task ':sdk:transformClassesWithAutoPatchTransformForDevelopRelease'.
> auto patch end successfully
```
![](/pic/QQ截图20171102112715.png)

补丁为了保证安全性，需要加密后再上传到Bmob后台，为了方便操作，我用MFC写了个Window程序，可以看下 [AesWindowsApplication](https://github.com/oubowu/AesWindowsApplication)

![](/pic/加解密工具.png)

上传到Bmob后台

![](/pic/Bmob后台.png)

在app模块使用library-develop-release.aar，在主页面MainActivity.class进行SDK初始化，SdkTest.init会执行补丁请求和加载的逻辑
```
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTvHello = (TextView) findViewById(R.id.tv_hello);

        findViewById(R.id.bt_sdk).setOnClickListener(this);

        checkPermissionAndCallSdk();

    }

    private void checkPermissionAndCallSdk() {
        boolean checkPermission = MPermissionUtils.getInstance()
                .checkPermission(this, REQUEST_PERMISSION_SUCCESS, Manifest.permission.READ_PHONE_STATE, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE);
        if (checkPermission) {
            SdkTest.init(this);
        }
    }
```

第一次启动APP并且断网的情况下，在点击事件调用SDK有bug的方法
```
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.bt_sdk:
                SdkTest.callBugMethod(this);
                mTvHello.setText(NdkHelper.getStrFromCPlus());
                break;
            default:
                break;
        }
    }
```

Logcat打印以下字符串转换整型抛出的异常
```
    FATAL EXCEPTION: main
    Process: com.oubowu.robustforsdk, PID: 18306
    java.lang.NumberFormatException: Invalid int: "Hello from C++"
```

将网络打开，第二次启动APP，网络请求下发正常的话，打印出我们使用的补丁版本是3，对应SDK版本是2.6.0；下载成功后放在/data/data/(包名)/files；并且应用成功了
```
11-03 09:48:43.893 19711-19711/com.oubowu.robustforsdk E/PRETTY_LOGGER: ┌────────────────────────────────────────────────────────────────────────────────────────────────────────────────
11-03 09:48:43.893 19711-19711/com.oubowu.robustforsdk E/PRETTY_LOGGER: │ Thread: main
11-03 09:48:43.893 19711-19711/com.oubowu.robustforsdk E/PRETTY_LOGGER: ├┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄
11-03 09:48:43.893 19711-19711/com.oubowu.robustforsdk E/PRETTY_LOGGER: │ From.Code  (PolicyQuery.java:264)
11-03 09:48:43.893 19711-19711/com.oubowu.robustforsdk E/PRETTY_LOGGER: │    a$1.done  (PatchManipulateImp.java:95)
11-03 09:48:43.893 19711-19711/com.oubowu.robustforsdk E/PRETTY_LOGGER: ├┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄
11-03 09:48:43.893 19711-19711/com.oubowu.robustforsdk E/PRETTY_LOGGER: │ Patch{sdkVersion='2.6.0', patchVersion='3', patchUrl=http://bmob-cdn-14435.b0.upaiyun.com/2017/10/16/37e20fe240ae64a2803b3d5ed7047be9.jar} com.oubowu.sdk.Patch@42588d90
11-03 09:48:43.893 19711-19711/com.oubowu.robustforsdk E/PRETTY_LOGGER: └────────────────────────────────────────────────────────────────────────────────────────────────────────────────

11-03 09:51:35.513 20875-20875/com.oubowu.robustforsdk E/PRETTY_LOGGER: ┌────────────────────────────────────────────────────────────────────────────────────────────────────────────────
11-03 09:51:35.513 20875-20875/com.oubowu.robustforsdk E/PRETTY_LOGGER: │ Thread: main
11-03 09:51:35.513 20875-20875/com.oubowu.robustforsdk E/PRETTY_LOGGER: ├┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄
11-03 09:51:35.523 20875-20875/com.oubowu.robustforsdk E/PRETTY_LOGGER: │ of.onPostExecute  (BmobFileDownloader.java:2095)
11-03 09:51:35.523 20875-20875/com.oubowu.robustforsdk E/PRETTY_LOGGER: │    a$1$1.done  (PatchManipulateImp.java:109)
11-03 09:51:35.523 20875-20875/com.oubowu.robustforsdk E/PRETTY_LOGGER: ├┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄
11-03 09:51:35.523 20875-20875/com.oubowu.robustforsdk E/PRETTY_LOGGER: │ 下载成功，/data/data/com.oubowu.robustforsdk/files/e-patch-2.6.0-4.jar
11-03 09:51:35.523 20875-20875/com.oubowu.robustforsdk E/PRETTY_LOGGER: └────────────────────────────────────────────────────────────────────────────────────────────────────────────────

11-03 09:51:35.533 20875-20875/com.oubowu.robustforsdk E/secret: /data/data/com.oubowu.robustforsdk/files/e-patch-2.6.0-4.jar
11-03 09:51:35.533 20875-20875/com.oubowu.robustforsdk E/secret: /data/data/com.oubowu.robustforsdk/files/d-e-patch-2.6.0-4.jar
11-03 09:51:35.623 20875-20891/com.oubowu.robustforsdk E/PRETTY_LOGGER: ┌────────────────────────────────────────────────────────────────────────────────────────────────────────────────
11-03 09:51:35.623 20875-20891/com.oubowu.robustforsdk E/PRETTY_LOGGER: │ Thread: pool-1-thread-1
11-03 09:51:35.623 20875-20891/com.oubowu.robustforsdk E/PRETTY_LOGGER: ├┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄
11-03 09:51:35.623 20875-20891/com.oubowu.robustforsdk E/PRETTY_LOGGER: │ PatchExecutor.applyPatchList  (PatchExecutor.java:71)
11-03 09:51:35.623 20875-20891/com.oubowu.robustforsdk E/PRETTY_LOGGER: │    SdkTest$3.onPatchApplied  (SdkTest.java:97)
11-03 09:51:35.623 20875-20891/com.oubowu.robustforsdk E/PRETTY_LOGGER: ├┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄
11-03 09:51:35.623 20875-20891/com.oubowu.robustforsdk E/PRETTY_LOGGER: │ PatchExecutor
11-03 09:51:35.623 20875-20891/com.oubowu.robustforsdk E/PRETTY_LOGGER: └────────────────────────────────────────────────────────────────────────────────────────────────────────────────

```

这时候点击点击事件，可以从log看到已经使用了修复的代码
```
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk W/System.err: java.lang.NumberFormatException: Invalid int: "Hello from C++"
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk W/System.err:     at java.lang.Integer.invalidInt(Integer.java:137)
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk W/System.err:     at java.lang.Integer.parse(Integer.java:374)
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk W/System.err:     at java.lang.Integer.parseInt(Integer.java:365)
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk W/System.err:     at java.lang.Integer.parseInt(Integer.java:331)
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk W/System.err:     at com.oubowu.sdk.lib.PatchManipulateImp.SdkTestPatch.callBugMethod(SdkTestPatch.java:163)
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk W/System.err:     at com.oubowu.sdk.lib.PatchManipulateImp.SdkTestPatchControl.accessDispatch(PatchTemplate.java)
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk W/System.err:     at com.meituan.robust.PatchProxy.accessDispatch(PatchProxy.java:61)
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk W/System.err:     at com.oubowu.sdk.SdkTest.callBugMethod(SdkTest.java)
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk W/System.err:     at com.oubowu.robustforsdk.MainActivity.onClick(MainActivity.java:46)
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk W/System.err:     at android.view.View.performClick(View.java:4444)
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk W/System.err:     at android.view.View$PerformClick.run(View.java:18457)
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk W/System.err:     at android.os.Handler.handleCallback(Handler.java:733)
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk W/System.err:     at android.os.Handler.dispatchMessage(Handler.java:95)
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk W/System.err:     at android.os.Looper.loop(Looper.java:136)
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk W/System.err:     at android.app.ActivityThread.main(ActivityThread.java:5113)
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk W/System.err:     at java.lang.reflect.Method.invokeNative(Native Method)
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk W/System.err:     at java.lang.reflect.Method.invoke(Method.java:515)
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk W/System.err:     at com.android.internal.os.ZygoteInit$MethodAndArgsCaller.run(ZygoteInit.java:796)
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk W/System.err:     at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:612)
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk W/System.err:     at dalvik.system.NativeStart.main(Native Method)
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk E/PRETTY_LOGGER: ┌────────────────────────────────────────────────────────────────────────────────────────────────────────────────
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk E/PRETTY_LOGGER: │ Thread: main
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk E/PRETTY_LOGGER: ├┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk E/PRETTY_LOGGER: │ SdkTestPatchControl.accessDispatch  (PatchTemplate.java:-1)
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk E/PRETTY_LOGGER: │    SdkTestPatch.callBugMethod  (SdkTestPatch.java:166)
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk E/PRETTY_LOGGER: ├┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk E/PRETTY_LOGGER: │ 我使用Robust热更新把空指针修复啦！！！
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk E/PRETTY_LOGGER: └────────────────────────────────────────────────────────────────────────────────────────────────────────────────
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk D/robust: invoke static  method is       No:  23  e
11-03 09:53:46.213 20875-20875/com.oubowu.robustforsdk E/PRETTY_LOGGER: ┌────────────────────────────────────────────────────────────────────────────────────────────────────────────────
11-03 09:53:46.223 20875-20875/com.oubowu.robustforsdk E/PRETTY_LOGGER: │ Thread: main
11-03 09:53:46.223 20875-20875/com.oubowu.robustforsdk E/PRETTY_LOGGER: ├┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄
11-03 09:53:46.223 20875-20875/com.oubowu.robustforsdk E/PRETTY_LOGGER: │ SdkTestPatch.callBugMethod  (SdkTestPatch.java:169)
11-03 09:53:46.223 20875-20875/com.oubowu.robustforsdk E/PRETTY_LOGGER: │    SdkTest$MyClass.call  (SdkTest.java:176)
11-03 09:53:46.223 20875-20875/com.oubowu.robustforsdk E/PRETTY_LOGGER: ├┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄
11-03 09:53:46.223 20875-20875/com.oubowu.robustforsdk E/PRETTY_LOGGER: │ 我使用Robust热更新新增了一个静态内部类
11-03 09:53:46.223 20875-20875/com.oubowu.robustforsdk E/PRETTY_LOGGER: └────────────────────────────────────────────────────────────────────────────────────────────────────────────────
```

# 以上就是我实践SDK热修复的思路和方法，希望能给读者带来一些用处。

## License

    Copyright 2017 Oubowu

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.





