# coding:utf-8

import toml
import urllib.request
import urllib.error
import sys
import os
import subprocess
import shutil

CONFIG_PATH = './config.toml'


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


# def replace(filepath, old, new):
#     """[replace old string to new from filepath]

#     Arguments:
#         filepath {[path]} -- [file path that needs to be replaced]
#         old {[string]} -- [old string]
#         new {[string]} -- [new string]
#     """
#     if not os.path.exists(filepath):
#         return False

#     cmd = "sed -i 's|%s|%s|g' %s " % (old, new, filepath)

#     status, output = getstatusoutput(cmd)
#     if status != 0:
#         print(' replace failed,'
#               'new is %s, old is %s, file is %s, status is %s, output is %s ' % (
#                   new, old, filepath, str(status), output))
#         return False
#     return True

def check_state(cfg):
    storage_type = cfg['data']['storage']['adapter_type']
    contract_type = cfg['data']['storage']['storage_controller_type']
    print("storage_type = ", storage_type)
    print("contract_type = ", contract_type)
    if storage_type != "blockchain.fisco-bcos":
        print("Now storage only support fisco-bcos!")
        sys.exit(1)
    if contract_type != "smart-contract.solidity":
        print("Now smart-contract only support solidity!")
        sys.exit(1)
    output_language = cfg['resource-generation']['output']['output_language']
    if output_language != "java":
        print("Now output_language only support java!")
        sys.exit(1)
    file_must_exists("./scripts/build_chain.sh")

if __name__ == "__main__":
    print("wedpr starter init...")
    abs_path = os.path.abspath(CONFIG_PATH)
    print("config path is %s" % abs_path)
    cfg = init()
    check_state(cfg)
    # table_name = cfg['data']['data_table_name_prefix']
    offline_resource_path = ""

    anonymous_voting = cfg['resource-generation']['workflow']['anonymous_voting']['enabled']
    anonymous_auction = cfg['resource-generation']['workflow']['hidden_asset']['enabled']
    selective_disclosure = cfg['resource-generation']['workflow']['anonymous_auction']['enabled']
    hidden_asset = cfg['resource-generation']['workflow']['selective_disclosure']['enabled']
    print("anonymous_voting_enable = {}".format(anonymous_voting))
    print("anonymous_auction = {}".format(anonymous_auction))
    print("selective_disclosure = {}".format(selective_disclosure))
    print("hidden_asset_enable = {}".format(hidden_asset))
    # print("table_name_prefix = {}".format(table_name))
    app_output_path = cfg['resource-generation']['output']['app_output_path']
    client_path = "{}/WeDPR-Client".format(app_output_path)
    console_path = "{}/WeDPR-Console".format(app_output_path)
    dir_must_not_exists(app_output_path)
    os.mkdir(app_output_path)
    shutil.copytree("./template/client",
                    "{}".format(client_path))
    shutil.copytree("./template/console",
                    "{}".format(console_path))
                    

    if 'offline_resource_path' in cfg['resource-generation']:
        offline_resource_path = cfg['resource-generation']['offline_resource_path']
        print("try to use resource in %s" % offline_resource_path)
        file_must_exists("{}/fisco-bcos".format(offline_resource_path))
        shutil.copy("{}/fisco-bcos".format(offline_resource_path),
                        "{}/fisco-bcos".format(app_output_path))
        dynamic_lib = '{}/WeDPR_dynamic_lib'.format(
            offline_resource_path)
        shutil.copytree("{}".format(dynamic_lib),
                        "{}/src/main/resources/WeDPR_dynamic_lib/".format(client_path))
        shutil.copytree("{}".format(dynamic_lib),
                        "{}/conf/WeDPR_dynamic_lib/".format(console_path))

    else:
        lib_download_link = "https://github.com/WeDPR/TestBinary/releases/download/v0.1/linux_WeDPR_dynamic_lib.tar.gz"
        lib_name = "linux_WeDPR_dynamic_lib.tar.tar.gz"
        print("download linux_WeDPR_dynamic_lib...")
        download_bin(lib_download_link, lib_name)
        (status, result)\
            = getstatusoutput('tar -zxf {} -C {}/src/main/resources/WeDPR_dynamic_lib/ && '.format(lib_name,
                                             client_path,
                                             ))
        print(result)
        (status, result)\
            = getstatusoutput('tar -zxf {} -C {}/conf/WeDPR_dynamic_lib/ && '
                              'rm {}'.format(lib_name,
                                             client_path,
                                             lib_name))
        print(result)
        node_download_link = 'https://github.com/WeDPR/TestBinary/releases/download/v0.1/mini-wedpr-fisco-bcos.tar.gz'
        node_name = "mini-wedpr-fisco-bcos.tar.gz"
        print("download WeDPR fisco-bcos blockchain node...")
        download_bin(node_download_link, node_name)
        (status, result)\
            = getstatusoutput('tar -zxf {} -C ./{} && '
                              'rm {}'.format(node_name,
                                             app_output_path,
                                             node_name))
        print(result)

    if anonymous_voting:
        vote_table_file = '{}/src/main/java/com/webank/wedpr/example/anonymousvoting/DemoMain.java'.format(
            client_path)
        file_must_exists(vote_table_file)
        
        anonymous_voting_jar = '{}/WeDPR-Java-SDK-anonymous-voting.jar'.format(
            offline_resource_path)
        if os.path.exists(anonymous_voting_jar) and os.path.isfile(anonymous_voting_jar):
            shutil.copy("{}".format(anonymous_voting_jar),
                        "{}/lib/WeDPR-Java-SDK-anonymous-voting.jar".format(client_path))
            shutil.copy("{}".format(anonymous_voting_jar),
                        "{}/conf/WeDPR-Java-SDK-anonymous-voting.jar".format(console_path))
        else:
            jar_download_link = 'https://github.com/WeDPR/TestBinary/releases/download/v0.1/WeDPR-Java-SDK-anonymous-voting.jar'
            name = "WeDPR-Java-SDK-anonymous-voting.jar"
            download_bin(jar_download_link, name)
            shutil.copy("./WeDPR-Java-SDK-anonymous-voting.jar",
                    "{}/lib/WeDPR-Java-SDK-anonymous-voting.jar".format(client_path))
            shutil.move("./WeDPR-Java-SDK-anonymous-voting.jar",
                    "{}/conf/WeDPR-Java-SDK-anonymous-voting.jar".format(console_path))

    else:
        shutil.rmtree(
            '{}/src/main/java/com/webank/wedpr/example/anonymousvoting'.format(client_path))
        shutil.rmtree(
            '{}/src/test/java/com/webank/wedpr/anonymousvoting'.format(client_path))
        os.remove(
            '{}/tools/run_anonymous_voting_client.sh'.format(console_path))

    if hidden_asset:
        asset_table_file = '{}/src/main/java/com/webank/wedpr/example/assethiding/DemoMain.java'.format(
            client_path)
        file_must_exists(asset_table_file)
        
        hidden_asset_jar = '{}/WeDPR-Java-SDK-asset-hiding.jar'.format(
            offline_resource_path)
        if os.path.exists(hidden_asset_jar) and os.path.isfile(hidden_asset_jar):
            shutil.copy("{}".format(hidden_asset_jar),
                        "{}/lib/WeDPR-Java-SDK-asset-hiding.jar".format(client_path))
            shutil.copy("{}".format(hidden_asset_jar),
                        "{}/conf/WeDPR-Java-SDK-asset-hiding.jar".format(console_path))
        else:
            jar_download_link = 'https://github.com/WeDPR/TestBinary/releases/download/v0.1/WeDPR-Java-SDK-asset-hiding.jar'
            name = "WeDPR-Java-SDK-asset-hiding.jar"
            download_bin(jar_download_link, name)
            shutil.copy("./WeDPR-Java-SDK-asset-hiding.jar",
                    "{}/lib/WeDPR-Java-SDK-asset-hiding.jar".format(client_path))
            shutil.move("./WeDPR-Java-SDK-asset-hiding.jar",
                    "{}/conf/WeDPR-Java-SDK-asset-hiding.jar".format(console_path))
    else:
        shutil.rmtree(
            '{}/src/main/java/com/webank/wedpr/example/assethiding'.format(client_path))
        shutil.rmtree(
            '{}/src/test/java/com/webank/wedpr/assethiding'.format(client_path))
        os.remove(
            '{}/run_hidden_asset_client.sh'.format(console_path))

    if anonymous_auction:
        anonymous_auction_table_file = '{}/src/main/java/com/webank/wedpr/example/anonymousauction/DemoMain.java'.format(
            client_path)
        file_must_exists(anonymous_auction_table_file)
        
        anonymous_auction_jar = '{}/WeDPR-Java-SDK-anonymous-auction.jar'.format(
            offline_resource_path)
        if os.path.exists(anonymous_auction_jar) and os.path.isfile(anonymous_auction_jar):
            shutil.copy("{}".format(anonymous_auction_jar),
                        "{}/lib/WeDPR-Java-SDK-anonymous-auction.jar".format(client_path))
            shutil.copy("{}".format(anonymous_auction_jar),
                        "{}/conf/WeDPR-Java-SDK-anonymous-auction.jar".format(console_path))
        else:
            jar_download_link = 'https://github.com/WeDPR/TestBinary/releases/download/v0.1/WeDPR-Java-SDK-anonymous-auction.jar'
            name = "WeDPR-Java-SDK-anonymous-auction.jar"
            download_bin(jar_download_link, name)
            shutil.copy("./WeDPR-Java-SDK-anonymous-auction.jar",
                    "{}/lib/WeDPR-Java-SDK-anonymous-auction.jar".format(client_path))
            shutil.move("./WeDPR-Java-SDK-anonymous-auction.jar",
                    "{}/conf/WeDPR-Java-SDK-anonymous-auction.jar".format(console_path))
    else:
        shutil.rmtree(
            '{}/src/main/java/com/webank/wedpr/example/anonymousauction'.format(client_path))
        shutil.rmtree(
            '{}/src/test/java/com/webank/wedpr/anonymousauction'.format(client_path))
        os.remove(
            '{}/run_anonymous_auction_client.sh'.format(console_path))


    if selective_disclosure:
        selective_disclosure_jar = '{}/WeDPR-Java-SDK-selective-disclosure.jar'.format(
            offline_resource_path)
        if os.path.exists(selective_disclosure_jar) and os.path.isfile(selective_disclosure_jar):
            shutil.copy("{}".format(selective_disclosure_jar),
                        "{}/lib/WeDPR-Java-SDK-selective-disclosure.jar".format(client_path))
            shutil.copy("{}".format(selective_disclosure_jar),
                        "{}/conf/WeDPR-Java-SDK-selective-disclosure.jar".format(console_path))
        else:
            jar_download_link = 'https://github.com/WeDPR/TestBinary/releases/download/v0.1/WeDPR-Java-SDK-selective-disclosure.jar'
            name = "WeDPR-Java-SDK-selective-disclosure.jar"
            download_bin(jar_download_link, name)
            shutil.copy("./WeDPR-Java-SDK-selective-disclosure.jar",
                    "{}/lib/WeDPR-Java-SDK-selective-disclosure.jar".format(client_path))
            shutil.move("./WeDPR-Java-SDK-asset-hiding.jar",
                    "{}/conf/WeDPR-Java-SDK-selective-disclosure.jar".format(console_path))
    else:
        shutil.rmtree(
            '{}/src/main/java/com/webank/wedpr/example/selectivedisclosure'.format(client_path))
        shutil.rmtree(
            '{}/src/test/java/com/webank/wedpr/selectivedisclosure'.format(client_path))
        os.remove(
            '{}/run_selective_disclosure_client.sh'.format(console_path))
            
    (status, result)\
        = getstatusoutput('bash ./scripts/build_chain.sh -l "127.0.0.1:4" -p 30300,20200,8545 -e {}/fisco-bcos -o {}/nodes'.format(app_output_path, app_output_path))
    print(result)

    dir_must_exists("{}/nodes".format(app_output_path))
    shutil.copy("{}/nodes/127.0.0.1/sdk/ca.crt".format(app_output_path),
                '{}/src/main/resources/ca.crt'.format(client_path))
    shutil.copy("{}/nodes/127.0.0.1/sdk/sdk.crt".format(app_output_path),
                '{}/src/main/resources/sdk.crt'.format(client_path))
    shutil.copy("{}/nodes/127.0.0.1/sdk/sdk.key".format(app_output_path),
                '{}/src/main/resources/sdk.key'.format(client_path))
    shutil.copy('{}/src/main/resources/applicationContext-example.xml'.format(client_path),
                '{}/src/main/resources/applicationContext.xml'.format(client_path))

    shutil.copy("{}/nodes/127.0.0.1/sdk/ca.crt".format(app_output_path),
                '{}/conf/ca.crt'.format(console_path))
    shutil.copy("{}/nodes/127.0.0.1/sdk/sdk.crt".format(app_output_path),
                '{}/conf/sdk.crt'.format(console_path))
    shutil.copy("{}/nodes/127.0.0.1/sdk/sdk.key".format(app_output_path),
                '{}/conf/sdk.key'.format(console_path))
    shutil.copy('{}/conf/applicationContext-example.xml'.format(console_path),
                '{}/conf/applicationContext.xml'.format(console_path))
