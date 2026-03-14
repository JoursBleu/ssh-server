cmake_minimum_required(VERSION 3.22.1)
project("ptycompat" C)

add_library(ptycompat SHARED pty_compat.c)
target_link_libraries(ptycompat log)
