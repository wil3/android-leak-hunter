Android-Leak-Hunger
------------------

This software allows for batch analysis of Android applications to identify potential information leakages through network connections. The assumption is based on the majority of apps using libraries to convert data model objects to and from JSON. This software locates the model objects used and extracts the data type and names of the class fields. 



Set-up
------

Install [MongoDb](http://docs.mongodb.org/manual/tutorial/install-mongodb-on-ubuntu/)

Install Maven if you don't already have it. To remove dependency headaches everything is handled by Maven. Also note this analysis software uses a modified version of Soot which can be found [here](https://github.com/wil3/soot) but is included in this build.

Build
-----

	mvn install


Run
---

Command line arguments:

Param 0: Full path to directory containing APKs to analyze

Param 1: Full path to directory to move APK after they have successfully been analyzed

Param 2: Full path to directory to move APK if no Models are found

Param 3: Full path to Android sdk platforms directory

Param 4: Full path to signature file. The signature file defines the method and argument object to extract to report field values from.

To enable logging add the following as a JVM argument:

	-Dlog4j.configuration=file:<path to log4j file>/log4j.properties


License
-------

Copyright (C) 2015 William Koch

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License along
with this program; if not, write to the Free Software Foundation, Inc.,
51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.