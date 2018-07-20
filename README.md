<!--

    Copyright (c) 2018-present Sonatype, Inc. All rights reserved.

    This program is licensed to you under the Apache License Version 2.0,
    and you may not use this file except in compliance with the Apache License Version 2.0.
    You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.

    Unless required by applicable law or agreed to in writing,
    software distributed under the Apache License Version 2.0 is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.

-->
# Sonatype OSS Index - Maven Integrations

![License](https://img.shields.io/github/license/sonatype/ossindex-maven.svg?label=License)

[Sonatype OSS Index](https://ossindex.sonatype.org/) integrations for [Apache Maven](https://maven.apache.org/).

## Building

### Requirements

* Apache Maven 3.3+
* JDK 7+ (10 is **NOT** supported)

### Build

    mvn clean install

## Site 

### Building

    ./mvnw clean install dionysus:build
    
### Publishing

    ./mvnw dionysus:publish
