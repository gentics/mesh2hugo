package com.gentics.mesh.hugo;

import java.io.File;
import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Runner {

	public static final String NAME = "mesh2hugo";

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

}
