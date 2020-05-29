# SQLiteWrapper Annotation
SQLiteWrapper Annotation

    
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
    		implementation 'com.github.ahsai001.SQLiteWrapper:SQLWAnnotation:{latest_release_version}'
    		annotationProcessor 'com.github.ahsai001.SQLiteWrapper:SQLWProcessor:{latest_release_version}'
	}
