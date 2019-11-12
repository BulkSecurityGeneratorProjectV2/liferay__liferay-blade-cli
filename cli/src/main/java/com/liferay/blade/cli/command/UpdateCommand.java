/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.liferay.blade.cli.command;

import aQute.bnd.version.Version;

import com.liferay.blade.cli.BladeCLI;
import com.liferay.blade.cli.util.BladeUtil;
import com.liferay.blade.cli.util.BladeVersions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import java.net.MalformedURLException;
import java.net.URL;

import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.security.MessageDigest;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.DatatypeConverter;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

/**
 * @author Gregory Amerson
 */
public class UpdateCommand extends BaseCommand<UpdateArgs> {

	public UpdateCommand() {
	}

	public UpdateCommand(BladeCLI bladeCLI) {
		setBlade(bladeCLI);
	}

	@Override
	public void execute() {
		BladeCLI bladeCLI = getBladeCLI();

		UpdateArgs updateArgs = getArgs();

		// String oldUrl = "https://releases.liferay.com/tools/blade-cli/latest/blade.jar";

		String currentVersion = "0.0.0.0";

		String updateVersion = "0.0.0.0";

		boolean checkOnly = updateArgs.isCheckOnly();

		boolean release = updateArgs.isRelease();

		boolean snapshots = updateArgs.isSnapshots();

		String releaseUpdateVersion = "";

		String snapshotUpdateVersion = "";

		String updateUrl = null;

		if (_hasUpdateUrlFromBladeDir()) {
			try {
				updateArgs.setUrl(new URL(_getUpdateUrlFromBladeDir()));
			}
			catch (MalformedURLException murle) {
				throw new RuntimeException(murle);
			}
		}

		if (updateArgs.getUrl() != null) {
			URL url = updateArgs.getUrl();

			updateUrl = url.toString();
		}

		try {
			BladeVersions versions = _getVersions();

			currentVersion = versions.getCurrentVersion();

			currentVersion = currentVersion.toUpperCase();

			snapshotUpdateVersion = versions.getSnapshotUpdateVersion();
			releaseUpdateVersion = versions.getReleasedUpdateVersion();
			
			boolean releaseShouldUpdate = _shouldUpdate(currentVersion, releaseUpdateVersion, updateUrl);
			boolean snapshotShouldUpdate = _shouldUpdate(currentVersion, snapshotUpdateVersion, updateUrl);

			boolean shouldUpdate;
			if (snapshots) {
				updateVersion = snapshotUpdateVersion;
				
			}
			else if (release) {
				updateVersion = releaseUpdateVersion;
			}
			else if (currentVersion.contains("SNAPSHOT")) {
				updateArgs.setSnapshots(true);

				updateVersion = snapshotUpdateVersion;
			}
			else {
				updateArgs.setRelease(true);

				updateVersion = releaseUpdateVersion;
			}
			
			if (Objects.equals(updateVersion, releaseUpdateVersion)) {
				shouldUpdate = releaseShouldUpdate;
			}
			else if (Objects.equals(updateVersion, snapshotUpdateVersion)) {
				shouldUpdate = snapshotShouldUpdate;
			}
			else
			{
				shouldUpdate = false;
			}
			


			
			if (updateUrl != null) {
				bladeCLI.out("Custom URL specified: " + updateUrl);
			}


			if (checkOnly) {

				String versionTag;

				if (Objects.equals(updateVersion, snapshotUpdateVersion)) {
					versionTag = "(snapshot)";
				}
				else if (Objects.equals(updateVersion, releaseUpdateVersion)) {
					versionTag = "(release)";
				}
				else {
					versionTag = "(custom)";
				}
				bladeCLI.out("Current blade version: " + currentVersion + " " + versionTag);
				bladeCLI.out("Latest release version: " + (releaseUpdateVersion == null ? "(Unavailable)" : releaseUpdateVersion));
				bladeCLI.out("Latest snapshot version: " + (snapshotUpdateVersion == null ? "(Unavailable)" : snapshotUpdateVersion));

				if (releaseUpdateVersion == null || !releaseShouldUpdate) {
					String message = "No new release updates are available for this version of blade.";


					bladeCLI.out(message);
				} else if (releaseShouldUpdate) {
					bladeCLI.out(
						"A new release update is available for blade: " + releaseUpdateVersion);
						if (updateArgs.isRelease() || currentVersion.contains("SNAPSHOT")) {
							bladeCLI.out("Pass the -r flag to 'blade update' to switch to release branch'");
						}
				}
				if (snapshotUpdateVersion == null || !snapshotShouldUpdate) {
					String message = "No new snapshot updates are available for this version of blade.";


					bladeCLI.out(message);
				}
				else if (snapshotShouldUpdate) {
					bladeCLI.out(
						"A new snapshot update is available for blade: " + snapshotUpdateVersion);
						if (updateArgs.isSnapshots() && !currentVersion.contains("SNAPSHOT")) {
							bladeCLI.out("Pass the -s flag to 'blade update' to switch to snapshots branch'");
						}
				}
				return;
			}

			String url = _getUpdateJarUrl(updateArgs);

			if (url == null) {
				String message;

				if (updateArgs.isSnapshots()) {
					message = "No new snapshot updates are available for this version of blade.";
				}
				else {
					message = "No new release updates are available for this version of blade.";
				}

				if (updateUrl != null) {
					bladeCLI.out("Custom URL specified: " + updateUrl);
				}

				bladeCLI.out(message);

				return;
			}

			if (shouldUpdate) {
				_performUpdate(url);
			}
			else {
				if (snapshots) {
					if (currentVersion.contains("SNAPSHOT")) {
						bladeCLI.out(
							"Current blade version " + currentVersion +
								" is greater than the latest snapshot version " + releaseUpdateVersion);
					}
					else {
						bladeCLI.out(
							"Current blade version " + currentVersion +
								" (released) is greater than the latest snapshot version " + releaseUpdateVersion);
					}
				}
				else {
					if (_equal(currentVersion, updateVersion)) {
						bladeCLI.out("Current blade version " + currentVersion + " is the latest released version.");
					}
					else {
						bladeCLI.out(
							"Current blade version " + currentVersion + " is higher than the latest version " +
								updateVersion);
						bladeCLI.out("Not updating, since downgrades are not supported at this time.");
						bladeCLI.out("If you want to force a downgrade, use the following command:");
						bladeCLI.out("\tjpm install -f " + url);
					}
				}
			}
		}
		catch (IOException ioe) {
			bladeCLI.error("Could not determine if blade update is available.");

			if (updateArgs.isTrace()) {
				PrintStream error = bladeCLI.error();

				ioe.printStackTrace(error);
			}
			else {
				bladeCLI.error("For more information run update with '--trace' option.");
			}
		}
	}

	@Override
	public Class<UpdateArgs> getArgsClass() {
		return UpdateArgs.class;
	}

	private static boolean _doesMD5Match(String url, boolean snapshot) {
		UpdateArgs updateArgs = new UpdateArgs();

		if (url != null) {
			try {
				updateArgs.setUrl(new URL(url));
			}
			catch (MalformedURLException murle) {
				throw new RuntimeException(murle);
			}
		}

		if (snapshot) {
			updateArgs.setSnapshots(true);
		}
		else {
			updateArgs.setRelease(true);
		}
		
		return _doesMD5Match(updateArgs);
	}

	private static boolean _doesMD5Match(UpdateArgs updateArgs) {
		try {
			String bladeJarMD5 = _getMD5(BladeUtil.getBladeJarPath());

			String updateJarMD5 = _readTextFileFromURL(_getUpdateJarMD5Url(updateArgs));

			return Objects.equals(updateJarMD5.toUpperCase(), bladeJarMD5);
		}
		catch (Exception e) {
		}

		return false;
	}

	private static boolean _equal(String currentVersion, String updateVersion) {
		if (currentVersion.contains("SNAPSHOT") && updateVersion.contains("-")) {
			Long currentSnapshot = _getBladeSnapshotVersion(currentVersion);

			Long updateSnapshot = _getNexusSnapshotVersion(updateVersion);

			if (updateSnapshot > currentSnapshot) {
				return false;
			}

			if (updateSnapshot < currentSnapshot) {
				return false;
			}

			if (updateSnapshot == currentSnapshot) {
				return true;
			}
		}

		Version currentSemver = _getVersionObject(currentVersion);

		Version updateSemver = _getVersionObject(updateVersion);

		return currentSemver.equals(updateSemver);
	}

	private static Long _getBladeSnapshotVersion(String currentVersion) {
		Matcher matcher = _bladeSnapshotPattern.matcher(currentVersion);

		matcher.find();

		return Long.parseLong(matcher.group(4));
	}

	private static String _getMD5(Path path) {
		try (FileChannel fileChannel = FileChannel.open(path)) {
			MappedByteBuffer mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());

			MessageDigest messageDigest = MessageDigest.getInstance("MD5");

			messageDigest.update(mappedByteBuffer);

			String md5Sum = DatatypeConverter.printHexBinary(messageDigest.digest());

			mappedByteBuffer.clear();

			return md5Sum.toUpperCase();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static Long _getNexusSnapshotVersion(String updateVersion) {
		Matcher matcher;

		matcher = _nexusSnapshotPattern.matcher(updateVersion);

		matcher.find();

		return Long.parseLong(matcher.group(4) + matcher.group(5));
	}

	private static String _getUpdateJarMD5Url(UpdateArgs updateArgs) throws IOException {
		String url = null;

		if (updateArgs.getUrl() != null) {
			URL updateUrlVar = updateArgs.getUrl();

			url = updateUrlVar.toString();
		}

		boolean release = updateArgs.isRelease();

		boolean snapshots = updateArgs.isSnapshots();

		String currentVersion = VersionCommand.getBladeCLIVersion();

		if (!release && !snapshots && currentVersion.contains("SNAPSHOT")) {
			snapshots = true;
		}

		if (url == null) {
			if (snapshots) {
				url = _SNAPSHOTS_REPO_URL;
			}
			else if (release) {
				url = _RELEASES_REPO_URL;
			}
		}

		Connection connection = Jsoup.connect(url + "maven-metadata.xml");

		connection = connection.parser(Parser.xmlParser());

		Document document = connection.get();

		Elements versionElements = document.select("version");

		Iterator<Element> it = versionElements.iterator();

		Collection<Element> elements = new HashSet<>();

		while (it.hasNext()) {
			Element versionElement = it.next();

			Node node = versionElement.childNode(0);

			String nodeString = node.toString();

			if (nodeString.contains("SNAPSHOT")) {
				if (!snapshots) {
					elements.add(versionElement);
				}
			}
			else {
				if (snapshots) {
					elements.add(versionElement);
				}
			}
		}

		versionElements.removeAll(elements);

		Element lastVersionElement = versionElements.last();

		String version = lastVersionElement.text();

		if (Objects.equals(url, _SNAPSHOTS_REPO_URL) || snapshots) {
			connection.url(url + "/" + version + "/maven-metadata.xml");

			document = connection.get();

			Elements valueElements = document.select("snapshotVersion > value");

			Element valueElement = valueElements.get(0);

			String snapshotVersion = valueElement.text();

			return url + "/" + version + "/com.liferay.blade.cli-" + snapshotVersion + ".jar.md5";
		}

		return url + "/" + version + "/com.liferay.blade.cli-" + version + ".jar.md5";
	}

	private static String _getUpdateJarUrl(UpdateArgs updateArgs) throws IOException {
		String url = null;

		if (updateArgs.getUrl() != null) {
			URL updateUrlVar = updateArgs.getUrl();

			url = updateUrlVar.toString();
		}

		boolean release = updateArgs.isRelease();

		boolean snapshots = updateArgs.isSnapshots();

		String currentVersion = VersionCommand.getBladeCLIVersion();

		if (!release && !snapshots && currentVersion.contains("SNAPSHOT")) {
			snapshots = true;
		}

		if (url == null) {
			if (snapshots) {
				url = _SNAPSHOTS_REPO_URL;
			}
			else if (release) {
				url = _RELEASES_REPO_URL;
			}
		}

		Connection connection = Jsoup.connect(url + "maven-metadata.xml");

		connection = connection.parser(Parser.xmlParser());

		Document document = connection.get();

		Elements versionElements = document.select("version");

		Iterator<Element> it = versionElements.iterator();

		Collection<Element> elements = new HashSet<>();

		while (it.hasNext()) {
			Element versionElement = it.next();

			Node node = versionElement.childNode(0);

			String nodeString = node.toString();

			if (nodeString.contains("SNAPSHOT")) {
				if (!snapshots) {
					elements.add(versionElement);
				}
			}
			else {
				if (snapshots) {
					elements.add(versionElement);
				}
			}
		}

		versionElements.removeAll(elements);

		Element lastVersion = versionElements.last();

		if (lastVersion == null) {
			return null;
		}

		String version = lastVersion.text();

		if (Objects.equals(url, _SNAPSHOTS_REPO_URL) || snapshots) {
			connection.url(url + "/" + version + "/maven-metadata.xml");

			document = connection.get();

			Elements valueElements = document.select("snapshotVersion > value");

			Element valueElement = valueElements.get(0);

			String snapshotVersion = valueElement.text();

			return url + "/" + version + "/com.liferay.blade.cli-" + snapshotVersion + ".jar";
		}

		return url + "/" + version + "/com.liferay.blade.cli-" + version + ".jar";
	}

	private static String _getUpdateUrlFromBladeDir() {
		String url = "no url";

		if (_hasUpdateUrlFromBladeDir()) {
			List<String> lines;

			try {
				lines = Files.readAllLines(Paths.get(_updateUrlFile.toURI()));

				url = lines.get(0);
			}
			catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}

		return url;
	}

	private static String _getUpdateVersion(boolean snapshotsArg) throws IOException {
		String url = _RELEASES_REPO_URL;

		if (snapshotsArg) {
			url = _SNAPSHOTS_REPO_URL;
		}

		if (_hasUpdateUrlFromBladeDir()) {
			url = _getUpdateUrlFromBladeDir();
		}

		Connection connection = Jsoup.connect(url + "maven-metadata.xml");

		connection = connection.parser(Parser.xmlParser());

		Document document = connection.get();

		Elements versionElements = document.select("version");

		Iterator<Element> it = versionElements.iterator();

		Collection<Element> elements = new HashSet<>();

		while (it.hasNext()) {
			Element versionElement = it.next();

			Node node = versionElement.childNode(0);

			String nodeString = node.toString();

			if (nodeString.contains("SNAPSHOT")) {
				if (!snapshotsArg) {
					elements.add(versionElement);
				}
			}
			else {
				if (snapshotsArg) {
					elements.add(versionElement);
				}
			}
		}

		versionElements.removeAll(elements);

		Element lastVersion = versionElements.last();

		String updateVersion = null;

		if (snapshotsArg) {
			if (lastVersion != null) {
				connection.url(url + lastVersion.text() + "/maven-metadata.xml");

				document = connection.get();

				Elements valueElements = document.select("snapshotVersion > value");

				Element valueElement = valueElements.get(0);

				updateVersion = valueElement.text();
			}
			else {
				return null;
			}
		}
		else {
			updateVersion = lastVersion.text();
		}

		return updateVersion;
	}

	private static Version _getVersionObject(String version) {
		Matcher matcher = _versionPattern.matcher(version);

		matcher.find();

		int currentMajor = Integer.parseInt(matcher.group(1));
		int currentMinor = Integer.parseInt(matcher.group(2));
		int currentPatch = Integer.parseInt(matcher.group(3));

		return new Version(currentMajor, currentMinor, currentPatch);
	}

	private static BladeVersions _getVersions() {
		String currentVersion = null;

		try {
			currentVersion = VersionCommand.getBladeCLIVersion();
		}
		catch (IOException ioe) {
			System.err.println("Could not determine current blade version, continuing with update.");
		}

		try {
			return new BladeVersions(currentVersion, _getUpdateVersion(false), _getUpdateVersion(true));
		}
		catch (IOException ioe) {
			throw new RuntimeException(ioe);
		}
	}

	private static boolean _hasUpdateUrlFromBladeDir() {
		boolean hasUpdate = false;

		if (_updateUrlFile.exists() && _updateUrlFile.isFile() && (_updateUrlFile.length() > 0)) {
			hasUpdate = true;
		}

		return hasUpdate;
	}

	private static String _readTextFileFromURL(String urlString) {
		try {
			StringBuilder sb = new StringBuilder();
			URL url = new URL(urlString);

			try (Scanner scanner = new Scanner(url.openStream())) {
				while (scanner.hasNextLine()) {
					sb.append(scanner.nextLine() + System.lineSeparator());
				}
			}

			String returnValue = sb.toString();

			return returnValue.trim();
		}
		catch (IOException ioe) {
			throw new RuntimeException(ioe);
		}
	}

	private static boolean _shouldUpdate(String currentVersion, String updateVersion, String url) {
		if (updateVersion == null) {
			return false;
		}
		
		boolean snapshot = currentVersion.contains("SNAPSHOT") && updateVersion.contains("-");

		Matcher matcher = _versionPattern.matcher(currentVersion);

		matcher.find();

		int currentMajor = Integer.parseInt(matcher.group(1));
		int currentMinor = Integer.parseInt(matcher.group(2));
		int currentPatch = Integer.parseInt(matcher.group(3));

		Version currentSemver = new Version(currentMajor, currentMinor, currentPatch);

		matcher = _versionPattern.matcher(updateVersion);

		matcher.find();

		int updateMajor = Integer.parseInt(matcher.group(1));
		int updateMinor = Integer.parseInt(matcher.group(2));
		int updatePatch = Integer.parseInt(matcher.group(3));

		Version updateSemver = new Version(updateMajor, updateMinor, updatePatch);

		boolean md5Match = _doesMD5Match(url, snapshot);

		if (!md5Match) {
			if (updateSemver.compareTo(currentSemver) > 0) {
				return true;
			}

			if (snapshot) {
				matcher = _bladeSnapshotPattern.matcher(currentVersion);

				matcher.find();

				Long currentSnapshot = Long.parseLong(matcher.group(4));

				matcher = _nexusSnapshotPattern.matcher(updateVersion);

				matcher.find();

				Long updateSnapshot = Long.parseLong(matcher.group(4) + matcher.group(5));

				if (updateSnapshot > currentSnapshot) {
					return true;
				}
			}
		}

		return false;
	}

	private void _performUpdate(String url) throws IOException {
		BladeCLI bladeCLI = getBladeCLI();

		bladeCLI.out("Updating from: " + url);

		if (BladeUtil.isWindows()) {
			_updateWindows(url);
		}
		else {
			_updateUnix(url);
		}
	}

	private void _updateUnix(String url) {
		BladeCLI bladeCLI = getBladeCLI();

		BaseArgs args = bladeCLI.getArgs();

		File baseDir = new File(args.getBase());

		try {
			Process process = BladeUtil.startProcess("jpm install -f " + url, baseDir);

			int errCode = process.waitFor();

			if (errCode == 0) {
				bladeCLI.out("Update completed successfully.");
			}
			else {
				bladeCLI.error("blade exited with code: " + errCode);
			}
		}
		catch (Exception e) {
			bladeCLI.error("Problem running jpm install.");
			bladeCLI.error(e);
		}
	}

	private void _updateWindows(String url) throws IOException {
		Path batPath = Files.createTempFile("jpm_install", ".bat");

		StringBuilder sb = new StringBuilder();

		ClassLoader classLoader = UpdateCommand.class.getClassLoader();

		try (InputStream inputStream = classLoader.getResourceAsStream("jpm_install.bat");
			Scanner scanner = new Scanner(inputStream)) {

			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();

				if (line.contains("%s")) {
					line = String.format(line, url);
				}

				sb.append(line);

				sb.append(System.lineSeparator());
			}
		}

		String batContents = sb.toString();

		Files.write(batPath, batContents.getBytes());

		Runtime runtime = Runtime.getRuntime();

		runtime.exec("cmd /c start \"\" \"" + batPath + "\"");
	}

	private static final String _BASE_CDN_URL = "https://repository-cdn.liferay.com/nexus/content/repositories/";

	private static final String _BLADE_CLI_CONTEXT = "com/liferay/blade/com.liferay.blade.cli/";

	private static final String _RELEASES_REPO_URL = _BASE_CDN_URL + "liferay-public-releases/" + _BLADE_CLI_CONTEXT;

	private static final String _SNAPSHOTS_REPO_URL = _BASE_CDN_URL + "liferay-public-snapshots/" + _BLADE_CLI_CONTEXT;

	private static final Pattern _bladeSnapshotPattern = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+).SNAPSHOT(\\d+)");
	private static final Pattern _nexusSnapshotPattern = Pattern.compile(
		"(\\d+)\\.(\\d+)\\.(\\d+)-(\\d+)\\.(\\d\\d\\d\\d)\\d\\d-\\d+");
	private static final File _updateUrlFile = new File(System.getProperty("user.home"), ".blade/update.url");
	private static final Pattern _versionPattern = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

}