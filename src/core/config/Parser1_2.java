package core.config;

import java.io.File;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import utilities.FileUtility;
import utilities.Function;
import utilities.JSONUtility;
import argo.jdom.JsonNode;
import argo.jdom.JsonNodeFactories;
import argo.jdom.JsonRootNode;
import core.keyChain.KeyChain;
import core.languageHandler.compiler.DynamicCompiler;
import core.userDefinedTask.TaskGroup;

public class Parser1_2 extends ConfigParser {

	private static final Logger LOGGER = Logger.getLogger(Parser1_2.class.getName());

	@Override
	protected String getVersion() {
		return "1.2";
	}

	@Override
	protected String getPreviousVersion() {
		return "1.1";
	}

	@Override
	protected JsonRootNode internalConvertFromPreviousVersion(JsonRootNode previousVersion) {
		try {
			JsonNode taskGroup = JsonNodeFactories.array(new Function<JsonNode, JsonNode>(){
				@Override
				public JsonNode apply(JsonNode taskGroup) {
					return JSONUtility.replaceChild(taskGroup, "tasks", JsonNodeFactories.array(
							new Function<JsonNode, JsonNode>() {
								@Override
								public JsonNode apply(JsonNode task) {
									String compiler = task.getStringValue("compiler");
									File f = new File(task.getStringValue("source_path"));
									File newFile = null;
									String newName = f.getName();

									if (compiler.equals("java")) {
										if (!newName.startsWith("CC_")) {
											newName = "CC_" + newName;
										}

										if (!newName.endsWith(".java")) {
											newName += ".java";
										}
									} else if (compiler.equals("python")) {
										if (!newName.startsWith("PY_")) {
											newName = "PY_" + newName;
										}

										if (!newName.endsWith(".py")) {
											newName = newName + ".py";
										}
									}
									newFile = FileUtility.renameFile(f, newName);

									if (FileUtility.fileExists(f)) {
										f.renameTo(newFile);
									}

									return JSONUtility.replaceChild(task, "source_path", JsonNodeFactories.string(newFile.getAbsolutePath()));
								}
							}.applyList(taskGroup.getArrayNode("tasks"))));
				}
			}.applyList(previousVersion.getArrayNode("task_groups")));

			JsonRootNode newRoot = JsonNodeFactories.object(
					JsonNodeFactories.field("version", JsonNodeFactories.string(getVersion())),
					JsonNodeFactories.field("global_hotkey", previousVersion.getNode("global_hotkey")),
					JsonNodeFactories.field("compilers", previousVersion.getNode("compilers")),
					JsonNodeFactories.field("task_groups", taskGroup)
					);

			return newRoot;
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Unable to convert json from previous version " + getPreviousVersion(), e);
			return null;
		}
	}

	@Override
	protected boolean internalExtractData(Config config, JsonRootNode root) {
		try {
			JsonNode globalHotkey = root.getNode("global_hotkey");
			config.setRECORD(KeyChain.parseJSON(globalHotkey.getArrayNode("record")));
			config.setREPLAY(KeyChain.parseJSON(globalHotkey.getArrayNode("replay")));
			config.setCOMPILED_REPLAY(KeyChain.parseJSON(globalHotkey.getArrayNode("replay_compiled")));

			for (JsonNode compilerNode : root.getArrayNode("compilers")) {
				String name = compilerNode.getStringValue("name");
				String path = compilerNode.getStringValue("path");
				String runArgs = compilerNode.getStringValue("run_args");

				DynamicCompiler compiler = config.compilerFactory().getCompiler(name);
				if (compiler != null) {
					compiler.setPath(new File(path));
					compiler.setRunArgs(runArgs);
				} else {
					throw new IllegalStateException("Unknown compiler " + name);
				}
			}

			List<TaskGroup> taskGroups = config.getBackEnd().getTaskGroups();
			taskGroups.clear();
			for (JsonNode taskGroupNode : root.getArrayNode("task_groups")) {
				TaskGroup taskGroup = TaskGroup.parseJSON(config.compilerFactory(), taskGroupNode);
				if (taskGroup != null) {
					taskGroups.add(taskGroup);
				}
			}

			if (taskGroups.isEmpty()) {
				taskGroups.add(new TaskGroup("default"));
			}
			config.getBackEnd().setCurrentTaskGroup(taskGroups.get(0));
			return true;
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Unable to parse json", e);
			return false;
		}
	}
}