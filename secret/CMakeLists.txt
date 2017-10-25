cmake_minimum_required(VERSION 3.4.1)

#C++编译
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=gnu++11")

#配置加载头文件
include_directories(${CMAKE_SOURCE_DIR}/alley/include)

#动态方式加载
add_library(openssl SHARED IMPORTED )
add_library(ssl SHARED IMPORTED )

#引入第三方.so库
set_target_properties(openssl PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}/libcrypto.so)
set_target_properties(ssl PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}/libssl.so)

add_library( # Sets the name of the library.
             secret-lib

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             src/main/cpp/secret-lib.cpp )

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

target_link_libraries( # Specifies the target library.
                       secret-lib
                       openssl
                       ssl
                       android

                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib} )