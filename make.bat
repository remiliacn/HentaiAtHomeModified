@if not exist build mkdir build
@cd src
@dir /s /b *.java > ../build/srcfiles.txt
@cd ..
javac -source 17 -target 17 -d ./build  @build/srcfiles.txt
