package com.gentics.mesh;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;

import com.gentics.mesh.core.rest.node.NodeListResponse;
import com.gentics.mesh.core.rest.node.NodeResponse;
import com.gentics.mesh.core.rest.node.PublishStatusModel;
import com.gentics.mesh.core.rest.schema.FieldSchema;
import com.gentics.mesh.core.rest.schema.SchemaListResponse;
import com.gentics.mesh.core.rest.schema.impl.SchemaResponse;
import com.gentics.mesh.parameter.LinkType;
import com.gentics.mesh.parameter.client.NodeParametersImpl;
import com.gentics.mesh.rest.client.MeshRestClient;

import io.vertx.core.json.JsonObject;

public class HugoExporter {

	public static void main(String[] args) throws IOException {
		FileUtils.deleteDirectory(new File("output"));

		String projectName = "demo";
		MeshRestClient client = MeshRestClient.create("demo.getmesh.io", 443, true);
		client.setLogin("admin", "admin");
		client.login().toCompletable().blockingAwait();

		SchemaListResponse schemaList = client.findSchemas().blockingGet();
		Map<String, SchemaResponse> schemas = new HashMap<>();
		schemaList.getData().forEach(s -> {
			schemas.put(s.getUuid(), s);
		});

		NodeListResponse list = client.findNodes(projectName, new NodeParametersImpl().setResolveLinks(LinkType.SHORT)).blockingGet();
		for (NodeResponse node : list.getData()) {
			Map<String, PublishStatusModel> langs = node.getAvailableLanguages();
			String schemaUuid = node.getSchema().getUuid();
			SchemaResponse schema = schemas.get(schemaUuid);
			String segment = schema.getSegmentField();

			for (String lang : langs.keySet()) {
				FieldSchema fieldSchema = schema.getField(segment);
				node = client
					.findNodeByUuid(projectName, node.getUuid(), new NodeParametersImpl().setResolveLinks(LinkType.SHORT).setLanguages(lang))
					.blockingGet();
				String path = node.getPath();
				if (fieldSchema.getType().equals("binary")) {
					Path outputPath = Paths.get("output/" + path);
					outputPath.getParent().toFile().mkdirs();
					try (InputStream ins = client.downloadBinaryField(projectName, node.getUuid(), lang, segment).blockingGet().getStream()) {
						Files.copy(ins, outputPath);
					}
				} else {
					StringBuffer buffer = new StringBuffer();
					JsonObject json = new JsonObject(node.getFields().toJson());
					buffer.append("---\n");
					for (String name : json.fieldNames()) {
						Object val = json.getValue(name);
						if (val instanceof String | val instanceof Integer | val instanceof Double) {
							buffer.append(name + ": \"" + val + "\"\n");
						} else {
							System.out.println(val.getClass().getName());
						}
					}
					buffer.append("---\n");
					Path outputPath = Paths.get("output/" + path + "/index.md");
					outputPath.getParent().toFile().mkdirs();
					Files.writeString(outputPath, buffer.toString());
				}
			}
		}

	}
}
