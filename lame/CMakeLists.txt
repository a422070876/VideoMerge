

cmake_minimum_required(VERSION 3.4.1)

aux_source_directory(src/main/cpp/lamemp3 lamemp3)

add_library(
             simple_lame
             SHARED
             src/main/cpp/simple_lame.cpp
              ${lamemp3}
              )


find_library(
              log-lib
              log )


target_link_libraries(
                       simple_lame
                       ${log-lib} )