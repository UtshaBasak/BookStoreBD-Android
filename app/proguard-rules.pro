# R8 rules for the release build. Libraries ship their own consumer rules; only
# what is specific to this app belongs here.

# Keep line numbers for readable crash reports, without the source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
