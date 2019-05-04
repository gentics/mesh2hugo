package com.gentics.mesh.hugo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import com.gentics.mesh.core.rest.node.FieldMap;
import com.gentics.mesh.core.rest.node.NodeListResponse;
import com.gentics.mesh.core.rest.node.NodeResponse;
import com.gentics.mesh.core.rest.node.PublishStatusModel;
import com.gentics.mesh.core.rest.node.field.MicronodeField;
import com.gentics.mesh.core.rest.node.field.NodeField;
import com.gentics.mesh.core.rest.node.field.impl.BooleanFieldImpl;
import com.gentics.mesh.core.rest.node.field.impl.DateFieldImpl;
import com.gentics.mesh.core.rest.node.field.impl.HtmlFieldImpl;
import com.gentics.mesh.core.rest.node.field.impl.NumberFieldImpl;
import com.gentics.mesh.core.rest.node.field.impl.StringFieldImpl;
import com.gentics.mesh.core.rest.node.field.list.MicronodeFieldList;
import com.gentics.mesh.core.rest.node.field.list.NodeFieldList;
import com.gentics.mesh.core.rest.node.field.list.impl.BooleanFieldListImpl;
import com.gentics.mesh.core.rest.node.field.list.impl.DateFieldListImpl;
import com.gentics.mesh.core.rest.node.field.list.impl.HtmlFieldListImpl;
import com.gentics.mesh.core.rest.node.field.list.impl.NumberFieldListImpl;
import com.gentics.mesh.core.rest.node.field.list.impl.StringFieldListImpl;
import com.gentics.mesh.core.rest.schema.FieldSchema;
import com.gentics.mesh.core.rest.schema.ListFieldSchema;
import com.gentics.mesh.core.rest.schema.SchemaListResponse;
import com.gentics.mesh.core.rest.schema.impl.SchemaResponse;
import com.gentics.mesh.core.rest.user.UserReference;
import com.gentics.mesh.parameter.LinkType;
import com.gentics.mesh.parameter.client.NodeParametersImpl;
import com.gentics.mesh.rest.client.MeshRestClient;

public class HugoExporter {

	private MeshRestClient client;
	private String url;
	private File output = new File("content");
	private String apiKey;
	private String projectName;

	public HugoExporter setApiKey(String apiKey) {
		this.apiKey = apiKey;
		return this;
	}

	public void setOutput(File output) {
		this.output = output;
	}

	public File getOutput() {
		return output;
	}

	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public void run() throws IOException {
		setupClient();
		export();
		System.out.println("-----");
		System.out.println("All done...");
	}

	private void setupClient() throws MalformedURLException {
		URL uri = new URL(url);
		boolean ssl = uri.getProtocol().startsWith("https");
		int port = uri.getPort();
		if (port == -1) {
			port = ssl ? 443 : 80;
		}

		String host = uri.getHost();

		client = MeshRestClient.create(host, port, ssl);
		if (apiKey != null) {
			client.setAPIKey(apiKey);
		}

	}

	public void cleanup() throws IOException {
		System.out.println("Cleaning output directory: " + output);
		FileUtils.deleteDirectory(output);
	}

	public void export() throws IOException {

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

			// Handle all contents of the node
			for (String lang : langs.keySet()) {
				node = client
					.findNodeByUuid(projectName, node.getUuid(), new NodeParametersImpl().setResolveLinks(LinkType.SHORT).setLanguages(lang))
					.blockingGet();
				exportNode(node, lang, schema);
			}
		}

	}

	private void exportNode(NodeResponse node, String lang, SchemaResponse schema) throws IOException {
		String path = node.getPath();

		String segmentFieldKey = schema.getSegmentField();
		FieldSchema segmentFieldSchema = schema.getField(segmentFieldKey);
		boolean hasBinarySegment = segmentFieldSchema.getType().equals("binary");

		if (hasBinarySegment) {
			Path outputPath = new File(output, path).toPath();
			outputPath.getParent().toFile().mkdirs();
			System.out.println("Exporting " + node.getUuid() + "/" + lang + " => " + outputPath);
			try (InputStream ins = client.downloadBinaryField(projectName, node.getUuid(), lang, segmentFieldKey).blockingGet().getStream()) {
				Files.copy(ins, outputPath, StandardCopyOption.REPLACE_EXISTING);
			}
		} else {
			StringBuffer buffer = new StringBuffer();
			buffer.append("---\n");

			// Node properties
			appendString(buffer, "path", node.getPath());
			appendString(buffer, "lang", node.getLanguage());
			appendString(buffer, "version", node.getVersion());
			appendString(buffer, "displayField", node.getDisplayField());
			appendString(buffer, "edited", node.getEdited());
			appendString(buffer, "created", node.getCreated());
			UserReference creator = node.getCreator();
			if (creator != null && creator.getFirstName() != null) {
				appendString(buffer, "creator", creator.getFirstName() + creator.getLastName());
			}
			UserReference editor = node.getEditor();
			if (editor != null && editor.getFirstName() != null) {
				appendString(buffer, "editor", editor.getFirstName() + editor.getLastName());
			}
			appendStringList("tags", buffer, node.getTags().stream().map(t -> t.getName()).collect(Collectors.toList()));

			// Node Fields
			for (FieldSchema fieldSchema : schema.getFields()) {
				extractField(buffer, fieldSchema, node.getFields());
			}
			buffer.append("---\n");
			String filename = "index.md";
			if (schema.isContainer()) {
				filename = "_index.md";
			}
			Path outputPath = new File(new File(output, path), filename).toPath();
			outputPath.getParent().toFile().mkdirs();
			System.out.println("Exporting " + node.getUuid() + "/" + lang + " => " + outputPath);
			FileUtils.writeStringToFile(outputPath.toFile(), buffer.toString(), Charset.defaultCharset());
		}

	}

	private void appendString(StringBuffer buffer, String key, String value) {
		buffer.append(key + ": \"" + sanitizeString(value) + "\"\n");
	}

	protected void extractField(StringBuffer buffer, FieldSchema fieldSchema, FieldMap fields) {
		String key = fieldSchema.getName();
		String outputKey = "fields_" + key;

		String type = fieldSchema.getType();
		switch (type) {
		case "html":
			HtmlFieldImpl htmlField = fields.getHtmlField(key);
			if (htmlField != null) {
				appendString(buffer, outputKey, htmlField.getHTML());
			}
			break;
		case "string":
			StringFieldImpl stringField = fields.getStringField(key);
			if (stringField != null) {
				appendString(buffer, outputKey, stringField.getString());
			}
			break;
		case "number":
			NumberFieldImpl numberField = fields.getNumberField(key);
			if (numberField != null) {
				buffer.append(outputKey + ": " + numberField.getNumber() + "\n");
			}
			break;
		case "boolean":
			BooleanFieldImpl booleanField = fields.getBooleanField(key);
			if (booleanField != null) {
				buffer.append(outputKey + ": " + booleanField.getValue() + "\n");
			}
			break;
		case "date":
			DateFieldImpl dateField = fields.getDateField(key);
			if (dateField != null) {
				appendString(buffer, outputKey, dateField.getDate());
			}
			break;
		case "micronode":
			MicronodeField micronodeField = fields.getMicronodeField(key);
			if (micronodeField != null) {
				// TODO add fields
				appendString(buffer, outputKey, micronodeField.getUuid());
			}
			break;
		case "node":
			NodeField nodeField = fields.getNodeField(key);
			if (nodeField != null) {
				appendString(buffer, outputKey, nodeField.getPath());
			}
			break;
		case "list":
			extractListField(buffer, fieldSchema, fields);
			break;
		case "binary":
			// Not exported
			break;
		default:
			throw new RuntimeException("Handling for field type {" + type + "} no supported");
		}
	}

	private String sanitizeString(String text) {
		if (text == null) {
			return null;
		}
		return text.replaceAll("\"", Matcher.quoteReplacement("\\\""));
	}

	private void extractListField(StringBuffer buffer, FieldSchema fieldSchema, FieldMap fields) {
		ListFieldSchema listFieldSchema = (ListFieldSchema) fieldSchema;
		String key = fieldSchema.getName();
		String outputKey = "fields_" + key;
		String listType = listFieldSchema.getListType();
		switch (listType) {
		case "node":
			NodeFieldList nodeField = fields.getNodeFieldList(key);
			if (nodeField != null) {
				List<String> nodeList = nodeField.getItems().stream()
					.map(n -> n.getPath())
					.map(n -> "\"" + n + "\"")
					.collect(Collectors.toList());
				buffer.append(outputKey + ": [" + String.join(",", nodeList) + "]\n");
			}
			break;
		case "string":
			StringFieldListImpl stringField = fields.getStringFieldList(key);
			if (stringField != null) {
				appendStringList(outputKey, buffer, stringField.getItems());
			}
			break;
		case "html":
			HtmlFieldListImpl htmlField = fields.getHtmlFieldList(key);
			if (htmlField != null) {
				appendStringList(outputKey, buffer, htmlField.getItems());
			}
		case "number":
			NumberFieldListImpl numberField = fields.getNumberFieldList(key);
			if (numberField != null) {
				List<String> numberList = numberField.getItems().stream().map(n -> n.toString()).collect(Collectors.toList());
				buffer.append(outputKey + ": [" + String.join(",", numberList) + "]\n");
			}
			break;
		case "date":
			DateFieldListImpl dateField = fields.getDateFieldList(key);
			if (dateField != null) {
				List<String> dateList = dateField.getItems();
				buffer.append(outputKey + ": [" + String.join(",", dateList) + "]\n");
			}
			break;
		case "micronode":
			MicronodeFieldList micronodeField = fields.getMicronodeFieldList(key);
			if (micronodeField != null) {
				List<MicronodeField> micronodeList = micronodeField.getItems();
				buffer.append(outputKey + ": [" + "]\n");
			}
			break;
		case "boolean":
			BooleanFieldListImpl booleanField = fields.getBooleanFieldList(key);
			if (booleanField != null) {
				List<String> booleanList = booleanField.getItems().stream().map(b -> b.toString()).collect(Collectors.toList());
				buffer.append(outputKey + ": [" + String.join(",", booleanList) + "]\n");
			}
			break;
		default:
			throw new RuntimeException("Handling for listfield type {" + listType + "} no supported");
		}

	}

	private void appendStringList(String key, StringBuffer buffer, List<String> list) {
		List<String> stringList = list.stream().map(e -> sanitizeString(e)).map(e -> "\"" + e + "\"")
			.collect(Collectors.toList());
		String str = String.join(",", stringList);
		buffer.append(key + ": [" + str + "]\n");
	}
}
