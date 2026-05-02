cmake_minimum_required(VERSION 3.22.1)
project("showraw")

# Oboe é distribuído com config CMake via AAR
find_package(oboe REQUIRED CONFIG)

add_library(
    showraw-audio
    SHARED
    audio_engine.cpp
)

target_link_libraries(
    showraw-audio
    oboe::oboe
    android
    log
)
