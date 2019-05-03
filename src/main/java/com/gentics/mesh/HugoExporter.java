package com.gentics.mesh;

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
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
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
import com.gentics.mesh.parameter.LinkType;
import com.gentics.mesh.parameter.client.NodeParametersImpl;
import com.gentics.mesh.rest.client.MeshRestClient;

public class HugoExporter {

	public static final String NAME = "mesh-hugo-exporter";

	private MeshRestClient client;
	private String url;
	private File output = new File("content");
	private String apiKey;
	private String projectName;

	public HugoExporter setApiKey(String apiKey) {
		this.apiKey = apiKey;
		return this;
	}

	private void setOutput(File output) {
		this.output = output;
	}

	private File getOutput() {
		return output;
	}

	private void setProjectName(String projectName) {
		this.projectName = projectName;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public static Options generateOptions() {
		Options options = new Options();
		options.addOption(new Option("help", "print this message"));
		options.addOption(new Option("o", "output", true, "Output directory (default: content)"));
		options.addOption(new Option("url", true, "Gentics Mesh API URL (e.g.  https://demo.getmesh.io/api/v1/)"));
		options.addOption(new Option("k", "key", true, "API Key"));
		options.addOption(new Option("c", "clean", false, "Clean output directory"));
		options.addOption(new Option("p", "project", true, "Project name to export"));
		return options;
	}

	public static void main(String[] args) throws IOException {
		handleArgs(args);
	}

	public static void handleArgs(String... args) throws IOException {
		Options options = generateOptions();
		CommandLineParser parser = new DefaultParser();
		try {
			HugoExporter exporter = new HugoExporter();
			CommandLine line = parser.parse(options, args);
			if (line.getOptions().length == 0) {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp(NAME, options);
				System.exit(2);
			}

			// output
			if (line.hasOption("o")) {
				exporter.setOutput(new File(line.getOptionValue("o")));
			}
			System.out.println("Using output: " + exporter.getOutput());

			// url
			if (line.hasOption("url")) {
				String url = line.getOptionValue("url");
				System.out.println("Using url: " + url);
				exporter.setUrl(url);
			} else {
				System.err.println("No connection url specified.");
				System.exit(11);
			}

			// key
			if (line.hasOption("key")) {
				String key = line.getOptionValue("key");
				System.out.println("Using key: " + key.replaceAll(".", "*"));
			} else {
				System.out.println("No key specified. Using anonymous access");
			}

			// project
			if (line.hasOption("p")) {
				String project = line.getOptionValue("p");
				System.out.println("Using project: " + project);
				exporter.setProjectName(project);
			} else {
				System.err.println("The project setting needs to be specified");
				System.exit(10);
			}

			// cleanup
			if (line.hasOption("c")) {
				exporter.cleanup();
			}

			System.out.println("-----");
			exporter.run();
			System.exit(0);
		} catch (ParseException exp) {
			System.err.println("Parsing failed.  Reason: " + exp.getMessage());
		}

	}

	private void run() throws IOException {
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
			for (FieldSchema fieldSchema : schema.getFields()) {
				extractField(buffer, fieldSchema, node.getFields());
			}
			buffer.append("---\n");
			Path outputPath = new File(new File(output, path), "index.md").toPath();
			outputPath.getParent().toFile().mkdirs();
			System.out.println("Exporting " + node.getUuid() + "/" + lang + " => " + outputPath);
			FileUtils.writeStringToFile(outputPath.toFile(), buffer.toString(), Charset.defaultCharset());
		}

	}

	private void extractField(StringBuffer buffer, FieldSchema fieldSchema, FieldMap fields) {
		String key = fieldSchema.getName();
		if (key.equals("slug")) {
			key = "mesh.slug";
		}
		if (key.equals("name")) {
			key = "mesh.name";
		}

		String type = fieldSchema.getType();
		switch (type) {
		case "html":
			HtmlFieldImpl htmlField = fields.getHtmlField(key);
			if (htmlField != null) {
				String val2Str = htmlField.getHTML().replaceAll("\"", "\\\"");
				buffer.append(key + ": \"" + val2Str + "\"\n");
			}
			break;
		case "string":
			StringFieldImpl stringField = fields.getStringField(key);
			if (stringField != null) {
				String valStr = stringField.getString().replaceAll("\"", "\\\"");
				buffer.append(key + ": \"" + valStr + "\"\n");
			}
			break;
		case "number":
			NumberFieldImpl numberField = fields.getNumberField(key);
			if (numberField != null) {
				buffer.append(key + ": " + numberField.getNumber() + "\n");
			}
			break;
		case "boolean":
			BooleanFieldImpl booleanField = fields.getBooleanField(key);
			if (booleanField != null) {
				buffer.append(key + ": " + booleanField.getValue() + "\n");
			}
			break;
		case "date":
			DateFieldImpl dateField = fields.getDateField(key);
			if (dateField != null) {
				buffer.append(key + ": \"" + dateField.getDate() + "\"\n");
			}
			break;
		case "micronode":
			MicronodeField micronodeField = fields.getMicronodeField(key);
			if (micronodeField != null) {
				// TODO add fields
				buffer.append(key + ": \"" + micronodeField.getUuid() + "\"\n");
			}
			break;
		case "node":
			NodeField nodeField = fields.getNodeField(key);
			if (nodeField != null) {
				buffer.append(key + ": \"" + nodeField.getPath() + "\"\n");
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

	private void extractListField(StringBuffer buffer, FieldSchema fieldSchema, FieldMap fields) {
		ListFieldSchema listFieldSchema = (ListFieldSchema) fieldSchema;
		String key = fieldSchema.getName();
		String listType = listFieldSchema.getListType();
		switch (listType) {
		case "node":
			NodeFieldList nodeField = fields.getNodeFieldList(key);
			if (nodeField != null) {
				List<String> nodeList = nodeField.getItems().stream()
					.map(n -> n.getPath())
					.map(n -> "\"" + n + "\"")
					.collect(Collectors.toList());
				buffer.append(key + ": [" + String.join(",", nodeList) + "]\n");
			}
			break;
		case "string":
			StringFieldListImpl stringField = fields.getStringFieldList(key);
			if (stringField != null) {
				extractStringList(key, buffer, stringField.getItems());
			}
			break;
		case "html":
			HtmlFieldListImpl htmlField = fields.getHtmlFieldList(key);
			if (htmlField != null) {
				extractStringList(key, buffer, htmlField.getItems());
			}
		case "number":
			NumberFieldListImpl numberField = fields.getNumberFieldList(key);
			if (numberField != null) {
				List<String> numberList = numberField.getItems().stream().map(n -> n.toString()).collect(Collectors.toList());
				buffer.append(key + ": [" + String.join(",", numberList) + "]\n");
			}
			break;
		case "date":
			DateFieldListImpl dateField = fields.getDateFieldList(key);
			if (dateField != null) {
				List<String> dateList = dateField.getItems();
				buffer.append(key + ": [" + String.join(",", dateList) + "]\n");
			}
			break;
		case "micronode":
			MicronodeFieldList micronodeField = fields.getMicronodeFieldList(key);
			if (micronodeField != null) {
				List<MicronodeField> micronodeList = micronodeField.getItems();
				buffer.append(key + ": [" + "]\n");
			}
			break;
		case "boolean":
			BooleanFieldListImpl booleanField = fields.getBooleanFieldList(key);
			if (booleanField != null) {
				List<String> booleanList = booleanField.getItems().stream().map(b -> b.toString()).collect(Collectors.toList());
				buffer.append(key + ": [" + String.join(",", booleanList) + "]\n");
			}
			break;
		default:
			throw new RuntimeException("Handling for listfield type {" + listType + "} no supported");
		}

	}

	private void extractStringList(String key, StringBuffer buffer, List<String> list) {
		List<String> stringList = list.stream().map(e -> e.replaceAll("\"", "\\\"")).map(e -> "\"" + e + "\"")
			.collect(Collectors.toList());
		String str = String.join(",", stringList);
		buffer.append(key + ": [" + str + "]\n");

	}
}
