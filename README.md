# SQLiteWrapper
SQLiteWrapper
Simple Wrapper for using sqlite database
    
How to use this library:

Step 1. Add the JitPack repository to your build file

Add it in your root build.gradle at the end of repositories:

	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}

Step 2. Add the dependency

	dependencies {
		implementation 'com.github.ahsai001.SQLiteWrapper:Core:{latest_release_version}'
    	implementation 'com.github.ahsai001.SQLiteWrapper:Annotation:{latest_release_version}'
    	annotationProcessor 'com.github.ahsai001.SQLiteWrapper:Processor:{latest_release_version}'
	}
