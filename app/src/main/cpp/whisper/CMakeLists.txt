cmake_minimum_required(VERSION 3.22.1)

project("whisper")

add_library(whisper SHARED
    whisper_jni.cpp
)

find_library(log-lib log)

target_link_libraries(whisper
    ${log-lib}
)
