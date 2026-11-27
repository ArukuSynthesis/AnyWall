cmake_minimum_required(VERSION 3.22.1)
project("anywall_rar" LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

file(GLOB LIBARCHIVE_SOURCES "${CMAKE_CURRENT_SOURCE_DIR}/libarchive/*.c")

add_library(
    anywall_rar
    SHARED
    anywall_rar_jni.cpp
    ${LIBARCHIVE_SOURCES}
)

target_include_directories(
    anywall_rar
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/libarchive
)

target_compile_definitions(
    anywall_rar
    PRIVATE
    HAVE_CONFIG_H=1
    _FILE_OFFSET_BITS=64
    _GNU_SOURCE=1
    LIBARCHIVE_STATIC=1
)

target_compile_options(
    anywall_rar
    PRIVATE
    -Os
    -ffunction-sections
    -fdata-sections
    -fvisibility=hidden
    -Wno-deprecated-declarations
    -Wno-unused-parameter
    -Wno-sign-compare
)

target_link_options(
    anywall_rar
    PRIVATE
    -Wl,--gc-sections
    -Wl,-s
)

find_library(ZLIB_LIB z)
find_library(LOG_LIB log)

target_link_libraries(
    anywall_rar
    ${ZLIB_LIB}
    ${LOG_LIB}
)
