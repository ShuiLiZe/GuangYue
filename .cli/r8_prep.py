import subprocess, os, sys, urllib.request, zipfile, shutil

def sh(cmd, cwd=None, timeout=1800):
    print(">>", cmd if isinstance(cmd, str) else " ".join(cmd), flush=True)
    p = subprocess.run(cmd, cwd=cwd, shell=isinstance(cmd, str), capture_output=True, text=True, timeout=timeout)
    if p.stdout:
        print(p.stdout[-4000:])
    if p.returncode != 0:
        print("STDERR:", p.stderr[-4000:])
    return p.returncode

def dl(url, dest):
    d = os.path.dirname(dest)
    os.makedirs(d, exist_ok=True)
    if os.path.exists(dest) and os.path.getsize(dest) > 100000:
        return dest
    print("downloading", url, flush=True)
    urllib.request.urlretrieve(url, dest)
    return dest

root = r"D:\workspaces\GuangYue"
cl = os.path.join(root, ".cli", "r8classpath")
os.makedirs(cl, exist_ok=True)

deps = [
    ("io.github.dokar3", "quickjs-kt-android", "1.0.15", "aar"),
    ("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.8.0", "jar"),
    ("org.jetbrains.kotlinx", "kotlinx-coroutines-android", "1.8.0", "jar"),
    ("org.jetbrains.kotlinx", "kotlinx-serialization-core-jvm", "1.7.3", "jar"),
    ("org.jetbrains.kotlinx", "kotlinx-serialization-json-jvm", "1.7.3", "jar"),
]
paths = []
for g, a, v, ext in deps:
    url = f"https://repo1.maven.org/maven2/{g.replace('.', '/')}/{a}/{v}/{a}-{v}.{ext}"
    p = dl(url, os.path.join(cl, f"{a}-{v}.{ext}"))
    paths.append(p)
    print(os.path.getsize(p), p)

print("done")
