# SQLiteWrapper
SQLiteWrapper is a simple wrapper for using sqlite database

SQLiteWrapper : 
	1. support multiple database
	1. support migration plan
	1. simple use
	1. without reflection
	1. lightweight
 
## Lookup Table
In this library also there is a Lookup table, that can used as key value pair storage like preferences in android (powered by SQLiteWrapper)
    
    
## How to use this library:

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
	
		implementation 'com.github.ahsai001:SQLiteWrapper:{latest_release_version}'
		
		or 
		
		implementation 'com.github.ahsai001.SQLiteWrapper:Core:{latest_release_version}'
    	implementation 'com.github.ahsai001.SQLiteWrapper:Annotation:{latest_release_version}'
    	annotationProcessor 'com.github.ahsai001.SQLiteWrapper:Processor:{latest_release_version}'
	}
