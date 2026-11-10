#!/usr/bin/python3
"""Keep Clang intrinsic headers ahead of MSVC fallbacks in the cross SDK."""
import os,sys
args=sys.argv[1:];out=[];i=0;compile_only=any(x in args for x in ['-c','-E'])
while i<len(args):
 a=args[i]
 if a=='-I' and i+1<len(args) and args[i+1].endswith('/__msvc_vcruntime_intrinsics'):
  out.extend(['-idirafter',args[i+1]]);i+=2;continue
 if a.startswith('-I') and a[2:].endswith('/__msvc_vcruntime_intrinsics'):
  out.extend(['-idirafter',a[2:]]);i+=1;continue
 if compile_only and a=='-L' and i+1<len(args):i+=2;continue
 if compile_only and a.startswith('-L') and len(a)>2:i+=1;continue
 out.append(a);i+=1
os.execv('/usr/bin/clang', ['/usr/bin/clang']+out)
