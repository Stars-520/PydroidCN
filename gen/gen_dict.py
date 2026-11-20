#!/usr/bin/env python3
"""
从 zh.tsv 生成 DictData.java。

两种模式：
  1) 有 res_dump.txt（aapt2 dump resources 的产物）→ 同时生成「资源名 → 中文」和
     「英文原文 → 中文」两张表，后者供 TextView 层替换。
  2) 没有 res_dump.txt → 只生成「资源名 → 中文」，TextHook 拿到空 TEXT 表后
     自动退化为纯资源层汉化，功能不崩。

用法：
    aapt2 dump resources base.apk > gen/res_dump.txt   # 可选
    python3 gen/gen_dict.py
"""
import os
import re
import io

HERE = os.path.dirname(os.path.abspath(__file__))
TSV = os.path.join(HERE, 'zh.tsv')
EN_TSV = os.path.join(HERE, 'strings_en.tsv')
DUMP = os.path.join(HERE, 'res_dump.txt')
OUT = os.path.join(HERE, '..', 'app', 'src', 'com', 'hsqs', 'pydroidcn', 'DictData.java')

# 这些资源名不参与「全局文本替换」：它们是代码片段、路径、配置默认值，
# 全局替换会污染编辑器内容或设置项。
NO_TEXT_REPLACE = {
    'enter_command_dialog_pip',
    'enter_command_pip_hint',
    'pref_shell_default',
    'pref_termtype_default',
    'pref_interpreter_name_default_value',
    'system_pylint_args_default_value',
    'watermark_label_prefix',
}


def load_zh():
    zh = {}
    with open(TSV, encoding='utf-8') as f:
        for line in f:
            line = line.rstrip('\n')
            if not line:
                continue
            k, _, v = line.partition('\t')
            if not k or not v:
                continue
            zh[k] = v
    return zh


def load_defaults():
    """返回 {资源名: 英文原文}。优先读轻量 strings_en.tsv，缺失时回退到 aapt2 dump。"""
    if os.path.exists(EN_TSV):
        out = {}
        with open(EN_TSV, encoding='utf-8') as f:
            for line in f:
                line = line.rstrip('\n')
                if not line:
                    continue
                k, _, v = line.partition('\t')
                if k:
                    out[k] = v
        return out

    if not os.path.exists(DUMP):
        return None

    lines = open(DUMP, encoding='utf-8', errors='replace').read().splitlines()

    start = None
    for i, l in enumerate(lines):
        if l.strip().startswith('type string '):
            start = i
            break
    if start is None:
        return None

    end = len(lines)
    for j in range(start + 1, len(lines)):
        if lines[j].startswith('  type ') or lines[j].startswith('Package'):
            end = j
            break

    out = {}
    cur = None
    for l in lines[start + 1:end]:
        m = re.match(r'\s+resource 0x[0-9a-f]+ string/(\S+)', l)
        if m:
            cur = m.group(1)
            continue
        m = re.match(r'\s+\(\) "(.*)"$', l)
        if m and cur and cur not in out:
            out[cur] = m.group(1)
    return out


def jstr(s):
    buf = []
    for ch in s:
        if ch == '\\':
            buf.append('\\\\')
        elif ch == '"':
            buf.append('\\"')
        elif ch == '\n':
            buf.append('\\n')
        elif ch == '\t':
            buf.append('\\t')
        elif ch == '\r':
            buf.append('\\r')
        else:
            buf.append(ch)
    return '"' + ''.join(buf) + '"'


def main():
    zh = load_zh()
    defaults = load_defaults()

    name_map = dict(zh)

    text_map = {}
    if defaults:
        for n, d in defaults.items():
            z = zh.get(n)
            if not z or not d:
                continue
            if n in NO_TEXT_REPLACE:
                continue
            if len(d) < 3:
                continue
            if d.startswith(('http', 'com.', 'MIIB', 'ca-app-pub', 'pub-')):
                continue
            # 同一个英文只保留第一次出现的译法
            text_map.setdefault(d, z)

    pairs = sorted(text_map.items(), key=lambda kv: -len(kv[0]))

    b = io.StringIO()
    b.write('package com.hsqs.pydroidcn;\n\n')
    b.write('import java.util.Map;\n\n')
    b.write('/**\n')
    b.write(' * 自动生成，勿手改。\n')
    b.write(' * 源: gen/zh.tsv' + (' + gen/res_dump.txt' if defaults else '') + '\n')
    b.write(' * 重新生成: python3 gen/gen_dict.py\n')
    b.write(' */\n')
    b.write('final class DictData {\n\n')
    b.write('    private DictData() {}\n\n')

    b.write('    static final int NAME_COUNT = %d;\n\n' % len(name_map))
    b.write('    static void fillName(Map<String, String> m) {\n')
    for k, v in name_map.items():
        b.write('        m.put(%s, %s);\n' % (jstr(k), jstr(v)))
    b.write('    }\n\n')

    b.write('    static final int TEXT_COUNT = %d;\n\n' % len(pairs))
    b.write('    static void fillText(Map<String, String> m) {\n')
    for k, v in pairs:
        b.write('        m.put(%s, %s);\n' % (jstr(k), jstr(v)))
    b.write('    }\n}\n')

    with open(OUT, 'w', encoding='utf-8') as f:
        f.write(b.getvalue())

    print('name=%d text=%d -> %s' % (len(name_map), len(pairs), OUT))


if __name__ == '__main__':
    main()