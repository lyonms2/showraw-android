@echo off
"C:\\Program Files\\Android\\Android Studio\\jbr\\bin\\java" ^
  --class-path ^
  "C:\\Users\\LeoPC\\.gradle\\caches\\modules-2\\files-2.1\\com.google.prefab\\cli\\2.1.0\\aa32fec809c44fa531f01dcfb739b5b3304d3050\\cli-2.1.0-all.jar" ^
  com.google.prefab.cli.AppKt ^
  --build-system ^
  cmake ^
  --platform ^
  android ^
  --abi ^
  arm64-v8a ^
  --os-version ^
  29 ^
  --stl ^
  c++_shared ^
  --ndk-version ^
  27 ^
  --output ^
  "C:\\Users\\LeoPC\\AppData\\Local\\Temp\\agp-prefab-staging17242710947473788824\\staged-cli-output" ^
  "C:\\Users\\LeoPC\\.gradle\\caches\\8.13\\transforms\\92c5f5b08957c94d8d823a8d1c116aed\\transformed\\oboe-1.9.3\\prefab"
