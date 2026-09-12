#!/usr/bin/env python3
# 脱敏过滤器（LANG-P1-02 任务 6.2）
# 用法：sanitize.sh <原始文件>，将脱敏结果输出到 stdout。
# 原始响应只应保存在被忽略的临时目录，提交前必须经过本脚本与人工抽查。
import re
import sys

PLACEHOLDER = "__REDACTED__"


def sanitize(text: str) -> str:
    text = re.sub(
        r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}",
        PLACEHOLDER,
        text,
    )
    text = re.sub(r"1[3-9]\d{9}", PLACEHOLDER, text)
    text = re.sub(
        r"(Bearer\s+)[^\"',\s}]+",
        r"\g<1>" + PLACEHOLDER,
        text,
    )
    text = re.sub(
        r"(?i)(([\"'])(password|passwd|pwd|session_secret|crypto_secret"
        r"|token|api[_-]?key|secret|authorization|cookie|set-cookie)\2\s*:\s*[\"'])"
        r"[^\"']*",
        r"\g<1>" + PLACEHOLDER,
        text,
    )
    text = re.sub(
        r"(?i)((password|passwd|pwd|token|secret|api[_-]?key)=)[^&\"'\s;]+",
        r"\g<1>" + PLACEHOLDER,
        text,
    )
    text = re.sub(r"(session=)[^;\"'\s]+", r"\g<1>" + PLACEHOLDER, text)
    text = re.sub(
        r"(postgres(?:ql)?://[^:\"'@]+:)[^@\"']+@",
        r"\g<1>" + PLACEHOLDER + "@",
        text,
    )
    text = re.sub(
        r"(redis://)(:[^@\"']+@)?",
        r"\g<1>" + PLACEHOLDER + "@",
        text,
    )
    text = re.sub(r"sk-[A-Za-z0-9_\-]+", PLACEHOLDER, text)
    return text


def main() -> int:
    if len(sys.argv) != 2:
        print("用法：sanitize.sh <原始文件>", file=sys.stderr)
        return 2
    with open(sys.argv[1], "r", encoding="utf-8") as f:
        content = f.read()
    sys.stdout.write(sanitize(content))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
