# coding:utf-8

import toml
import urllib.request
import urllib.error
import sys
import os
import subprocess
import shutil

CONFIG_PATH = './wedpr_starter_config.toml'

def init():
    """[init function]
    """
    file = toml.load(CONFIG_PATH)
    # init pwd dir
    # print("file is {}", file)
    # parser mchain.conf for project initialize
    return file

def _hook_func(num, block_size, total_size):
    """[get download msg]

    Arguments:
        num {[type]} -- [description]
        block_size {[type]} -- [description]
        total_size {[type]} -- [description]
    """

    precent = min(100, 100.0*num*block_size/total_size)
    sys.stdout.write('Downloading progress %.2f%%\r' % (precent))
    sys.stdout.flush()


def chunk_report(bytes_so_far, chunk_size, total_size):
    """[summary]

    Arguments:
        bytes_so_far {[type]} -- [description]
        chunk_size {[type]} -- [description]
        total_size {[type]} -- [description]
    """
    percent = float(bytes_so_far) / total_size
    percent = round(percent*100, 2)
    sys.stdout.write("Downloaded %d of %d bytes (%0.2f%%)\r" %
                     (bytes_so_far, total_size, percent))

    if bytes_so_far >= total_size:
        sys.stdout.write('\n')


def chunk_read(response, chunk_size=8192, report_hook=None, ):
    """output download
    """
    total_size = response.info().getheader('Content-Length').strip()
    total_size = int(total_size)
    bytes_so_far = 0
    data = []

    while 1:
        chunk = response.read(chunk_size)
        bytes_so_far += len(chunk)

        if not chunk:
            break

        data += chunk
        if report_hook:
            report_hook(bytes_so_far, chunk_size, total_size)

    return "".join(data)


def download_bin(_download_link, _package_name):
    """dowloand
    """
    import ssl
    ssl._create_default_https_context = ssl._create_unverified_context
    urllib.request.urlretrieve(
        _download_link, _package_name, _hook_func)


def getstatusoutput(cmd):
    """replace commands.getstatusoutput

    Arguments:
        cmd {[string]}
    """

    get_cmd = subprocess.Popen(
        cmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    ret = get_cmd.communicate()
    out = ret[0]
    err = ret[1]
    output = ''
    if not out is None:
        output = output + out.decode('utf-8')
    if not err is None:
        output = output + err.decode('utf-8')

    return (get_cmd.returncode, output)

def file_must_exists(_file):
    """[utils]

    Arguments:
        _file {[type]} -- [description]

    Raises:
        MCError -- [description]
    """

    if not (os.path.exists(_file) and os.path.isfile(_file)):
        print(' %s not exist!' % _file)
        sys.exit(1)


def file_must_not_exists(_file):
    """[utils]

    Arguments:
        _file {[type]} -- [description]

    Raises:
        MCError -- [description]
    """

    if os.path.exists(_file) and os.path.isfile(_file):
        print(' %s exist! pls delete it!' % _file)
        sys.exit(1)


def dir_must_exists(_dir):
    """[utils]

    Arguments:
        _dir {[type]} -- [description]

    Raises:
        MCError -- [description]
    """

    if not os.path.exists(_dir):
        print(' %s not existed!' % _dir)
        sys.exit(1)


def dir_must_not_exists(_dir):
    """[utils]

    Arguments:
        _dir {[type]} -- [description]

    Raises:
        MCError -- [description]
    """

    if os.path.exists(_dir):
        print(' %s existed! pls delete it!' % _dir)
        sys.exit(1)

def replace(filepath, old, new):
    """[replace old string to new from filepath]

    Arguments:
        filepath {[path]} -- [file path that needs to be replaced]
        old {[string]} -- [old string]
        new {[string]} -- [new string]
    """
    if not os.path.exists(filepath):
        return False

    cmd = "sed -i 's|%s|%s|g' %s " % (old, new, filepath)

    status, output = getstatusoutput(cmd)
    if status != 0:
        print(' replace failed,'
                     'new is %s, old is %s, file is %s, status is %s, output is %s '%(
                     new, old, filepath, str(status), output))
        return False
    return True

if __name__ == "__main__":
    print("wedpr starter init...")
    cfg = init() 
    storage = cfg['data']['storage']['adapter_type']
    # print("data = ", storage)
    anonymous_auction = cfg['resource-anonymous_auction']['workflow']['enabled']
    anonymous_voting = cfg['resource-anonymous_voting']['workflow']['enabled']
    hidden_asset = cfg['resource-hidden_asset']['workflow']['enabled']
    table_name = cfg['data']['data_class_guid']
    print("anonymous_auction_enable = {}".format(anonymous_auction))
    print("anonymous_voting_enable = {}".format(anonymous_voting))
    print("hidden_asset_enable = {}".format(hidden_asset))
    print("table_name = {}".format(table_name))


    sdk_download_link = 'https://github.com/HaoXuan40404/WeDPR-Java-SDK.git'
    sdk_name = "WeDPR-Java-SDK"
    node_download_link = 'https://github.com/WeDPR/TestBinary/releases/download/v0.1/mini-wedpr-fisco-bcos.tar.gz'
    node_name = "mini-wedpr-fisco-bcos.tar.gz"
    dir_must_not_exists(sdk_name)
    file_must_not_exists('fisco-bcos')
    download_bin(node_download_link, node_name)
    
    (status, result)\
        = getstatusoutput('tar -zxf {} -C ./ && '
                          'rm {}'.format(node_name,
                                         node_name))
    print(result)
    file_must_exists('fisco-bcos')
    print("waiting for download wedpr-java-sdk")
    (status, result)\
        = getstatusoutput('git clone {}'.format(sdk_download_link))
    print(result)
    
    dir_must_exists(sdk_name)
    if not anonymous_voting:
        # shutil.rmtree('{}/src/main/java/com/webank/wedpr/anonymousvoting'.format(sdk_name))
        shutil.rmtree('./{}/src/main/java/com/webank/wedpr/example/anonymousvoting'.format(sdk_name))
        shutil.rmtree('./{}/src/test/java/com/webank/wedpr/anonymousvoting'.format(sdk_name))
    else: 
        vote_table_file = '{}/src/test/java/com/webank/wedpr/anonymousvoting/DemoMain.java'.format(sdk_name)
        file_must_exists(vote_table_file)
        replace(vote_table_file, 'voter_example_', 'voter_example_{}'.format(table_name))
        replace(vote_table_file, 'counter_example_', 'counter_example_{}'.format(table_name))
    if not hidden_asset:
        # shutil.rmtree('{}/src/main/java/com/webank/wedpr/assethiding'.format(sdk_name))
        shutil.rmtree('./{}/src/main/java/com/webank/wedpr/example/assethiding'.format(sdk_name))
        shutil.rmtree('./{}/src/test/java/com/webank/wedpr/assethiding'.format(sdk_name))
    else:
        asset_table_file = './{}/src/test/java/com/webank/wedpr/assethiding/DemoMain.java'.format(sdk_name)
        file_must_exists(asset_table_file)
        replace(asset_table_file, 'hidden_asset_example', 'hidden_asset_example_{}'.format(table_name))
    
    (status, result)\
        = getstatusoutput('bash build_chain.sh -l "127.0.0.1:4" -p 30300,20200,8545 -e ./fisco-bcos')
    print(result)

    dir_must_exists("./nodes")
    shutil.copy("./nodes/127.0.0.1/sdk/ca.crt", './{}/src/main/resources/ca.crt'.format(sdk_name))
    shutil.copy("./nodes/127.0.0.1/sdk/sdk.crt", './{}/src/main/resources/sdk.crt'.format(sdk_name))
    shutil.copy("./nodes/127.0.0.1/sdk/sdk.key", './{}/src/main/resources/sdk.key'.format(sdk_name))
    shutil.copy('./{}/src/main/resources/applicationContext-example.xml'.format(sdk_name), './{}/src/main/resources/applicationContext.xml'.format(sdk_name))
