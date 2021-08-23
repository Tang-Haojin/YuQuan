#!/usr/bin/python3

import re
import os
import shutil
src = open('./build/sim/TestTop.v', 'r')
code = src.read()
src.close()

pat_slash = re.compile(r'(( |\t)*//.*\n)')
code = pat_slash.sub('\n', code)

embed_star = r'((/\*)([^*]*)(?!(\*/))([^*]*)(?!<(/\*))(\*/))'
pat_embed = re.compile(embed_star, re.S)
code = pat_embed.sub('', code)
code = pat_embed.sub('', code)

pat_module = re.compile(r'(module\s+(.*?)endmodule)', re.S)
pat_name = re.compile(r'module\s+(\w+)\s*\(', re.S)

if os.path.exists(r'./build/sim/files/'):
    shutil.rmtree(r'./build/sim/files/')

os.makedirs(r'./build/sim/files/')

srch1 = 'True'
while srch1:
    srch1 = pat_module.search(code)
    if srch1:
        srch2 = pat_name.search(srch1.group())
    else:
        break
    code = pat_module.sub('', code, count=1)

    src = open('./build/sim/files/' + srch2.group(1) + '.v', 'w')
    src.write(srch1.group())
    src.close()
