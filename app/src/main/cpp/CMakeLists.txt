cmake_minimum_required(VERSION 3.22.1)

project("whisper_jni")

# Creates and names a library, sets it as either STATIC or SHARED, and provides the relative paths to its source code.
add_library(
    whisper_jni
    SHARED
    WhisperJni.cpp
)

# Searches for a specified prebuilt library and stores the path as a variable.
find_library(
    log-lib
    log
)

# Specifies libraries CMake should link to your target library.
target_link_libraries(
    whisper_jni
    ${log-lib}
)
