# Mesh2Hugo

Mesh2Hugo works by exporting all stored contents (nodes, binaries) to an output directory.

The content fields of the headless CMS data (node fields) will be exported as markdown files which contain parameter as [front-matter](https://gohugo.io/content-management/front-matter/).

Example Output:

```
---
path: "/aircrafts/space-shuttle"
lang: "en"
version: "1.0"
displayField: "name"
edited: "2019-04-11T12:47:20Z"
created: "2019-04-11T12:47:20Z"
tags: ["Black","White","Hydrogen"]
fields_slug: "space-shuttle"
fields_name: "Space Shuttle"
fields_weight: 22700
fields_SKU: 9
fields_price: 1.92E11
fields_stocklevel: 0
fields_description: "The Space Shuttle was a partially reusable low Earth orbital spacecraft system operated by the U.S. National Aeronautics and Space Administration (NASA)."
fields_vehicleImage: "/images/sts.jpg"
---
```


A custom hugo theme can access these fields to render the page via the Params directive. (e.g.: `{{.Params.fields_name}}`)


## Usage

```
java -jar mesh2hugo-1.0.0.jar 
usage: mesh2hugo
 -c,--clean           Clean output directory
 -help                print this message
 -k,--key <arg>       API Key
 -o,--output <arg>    Output directory (default: content)
 -p,--project <arg>   Project name to export
 -url <arg>           Gentics Mesh API URL (e.g.
                      https://demo.getmesh.io/api/v1/)
```

Example:
```
java -jar mesh2hugo-1.0.0.jar -c -p demo -url https://demo.getmesh.io
```

This will export the Gentics Mesh demo server contents into the content directory.

## Theme

An example site and theme can be found in the `example` directory.

## Guide

A guide can be found in the Gentics Mesh documentation. https://getmesh.io/docs/guides/mesh-hugo/
