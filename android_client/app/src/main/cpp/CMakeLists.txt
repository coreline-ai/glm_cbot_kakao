cmake_minimum_required(VERSION 3.22.1)
project(cbot-secrets)

add_library(
    cbot-secrets
    SHARED
    native-secrets.cpp
)

target_compile_definitions(
    cbot-secrets
    PRIVATE
    GLM_KEY_PART_A=${GLM_KEY_PART_A}
    GLM_KEY_PART_B=${GLM_KEY_PART_B}
    GLM_KEY_PART_C=${GLM_KEY_PART_C}
    OPENAI_KEY_PART_A=${OPENAI_KEY_PART_A}
    OPENAI_KEY_PART_B=${OPENAI_KEY_PART_B}
    OPENAI_KEY_PART_C=${OPENAI_KEY_PART_C}
)

find_library(
    log-lib
    log
)

target_link_libraries(
    cbot-secrets
    ${log-lib}
)
