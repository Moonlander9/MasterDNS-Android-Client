cmake_minimum_required(VERSION 3.22.1)

project(mdvpn_tunnel_jni)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
enable_language(ASM)

set(HEV_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/third_party/hev-socks5-tunnel)

file(GLOB_RECURSE HEV_TASK_SOURCES
    ${HEV_ROOT}/third-part/hev-task-system/src/*.c
)
list(APPEND HEV_TASK_SOURCES
    ${HEV_ROOT}/third-part/hev-task-system/src/kern/task/hev-task-execute.S
)

add_library(hev_task_system STATIC ${HEV_TASK_SOURCES})
target_include_directories(hev_task_system
    PRIVATE
    ${HEV_ROOT}/third-part/hev-task-system/src
    ${HEV_ROOT}/third-part/hev-task-system/include
)
target_compile_options(hev_task_system PRIVATE -fvisibility=hidden)
target_compile_definitions(hev_task_system
    PRIVATE
    ENABLE_STACK_OVERFLOW_DETECTION
    ENABLE_MEMALLOC_SLICE
    ENABLE_IO_SPLICE_SYSCALL
    CONFIG_STACK_BACKEND=STACK_MMAP
    CONFIG_STACK_OVERFLOW_DETECTION=1
    CONFIG_MEMALLOC_SLICE_ALIGN=64
    CONFIG_MEMALLOC_SLICE_MAX_SIZE=4096
    CONFIG_MEMALLOC_SLICE_MAX_COUNT=1000
    CONFIG_SCHED_CLOCK=CLOCK_NONE
)

file(GLOB_RECURSE HEV_YAML_SOURCES
    ${HEV_ROOT}/third-part/yaml/src/*.c
)

add_library(hev_yaml STATIC ${HEV_YAML_SOURCES})
target_include_directories(hev_yaml PRIVATE ${HEV_ROOT}/third-part/yaml/src)
target_compile_definitions(hev_yaml
    PRIVATE
    YAML_VERSION_MAJOR=0
    YAML_VERSION_MINOR=2
    YAML_VERSION_PATCH=5
    YAML_VERSION_STRING=\"0.2.5\"
)

file(GLOB_RECURSE HEV_LWIP_SOURCES
    ${HEV_ROOT}/third-part/lwip/src/*.c
)

add_library(hev_lwip STATIC ${HEV_LWIP_SOURCES})
target_include_directories(hev_lwip
    PRIVATE
    ${HEV_ROOT}/third-part/lwip/src/include
    ${HEV_ROOT}/third-part/lwip/src/ports/include
)
target_compile_definitions(hev_lwip PRIVATE FD_SET_DEFINED SOCKLEN_T_DEFINED)

file(GLOB_RECURSE HEV_TUNNEL_SOURCES
    ${HEV_ROOT}/src/*.c
)
list(FILTER HEV_TUNNEL_SOURCES EXCLUDE REGEX ".*/hev-jni\\.c$")

add_library(hev_socks5_tunnel STATIC ${HEV_TUNNEL_SOURCES})
target_include_directories(hev_socks5_tunnel
    PRIVATE
    ${HEV_ROOT}/src
    ${HEV_ROOT}/src/misc
    ${HEV_ROOT}/src/core/include
    ${HEV_ROOT}/third-part/yaml/include
    ${HEV_ROOT}/third-part/lwip/src/include
    ${HEV_ROOT}/third-part/lwip/src/ports/include
    ${HEV_ROOT}/third-part/hev-task-system/include
)
target_compile_definitions(hev_socks5_tunnel
    PRIVATE
    FD_SET_DEFINED
    SOCKLEN_T_DEFINED
    ENABLE_LIBRARY
    COMMIT_ID=\"2.14.0\"
)
target_link_options(hev_socks5_tunnel
    PRIVATE
    -Wl,-z,max-page-size=16384
    -Wl,-z,common-page-size=16384
)
target_link_libraries(hev_socks5_tunnel
    PRIVATE
    hev_task_system
    hev_yaml
    hev_lwip
    m
    log
)

add_library(mdvpn_tunnel_jni SHARED tunnel_jni.cpp)

target_link_libraries(mdvpn_tunnel_jni
    PRIVATE
    hev_socks5_tunnel
    log
    android
)

target_link_options(mdvpn_tunnel_jni
    PRIVATE
    -Wl,-z,max-page-size=16384
    -Wl,-z,common-page-size=16384
)
