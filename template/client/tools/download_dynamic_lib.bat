@ECHO on

REM WeDPR-Java-SDK bat script

set compatibility_version=0.1
set CURRENTDIR=%cd%

IF not EXIST %CURRENTDIR%\..\src\main\resources (
    echo "%CURRENTDIR%\..\src\main\resources does not exist, please check!"
    EXIT /B 1
)
IF not EXIST %CURRENTDIR%\..\src\main\resources\WeDPR_dynamic_lib (
    set package_name=windows_WeDPR_dynamic_lib.tar.gz
    set Download_Link=https://github.com/WeDPR/TestBinary/releases/download/v%compatibility_version%/%package_name%
    curl -LO %Download_Link%
    tar -zxf %package_name% -C %CURRENTDIR%\..\src\main\resources\
    del %package_name%
    REM echo "Downloading WeDPR dynamic lib to %CURRENTDIR%\..\src\main\resource successful!"
    EXIT /B 0
)
echo "%CURRENTDIR%\..\src\main\resources\WeDPR_dynamic_lib existed, continue!"
