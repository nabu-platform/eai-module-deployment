package be.nabu.eai.module.deployment.deploy;

import java.util.ArrayList;
import java.util.List;

import be.nabu.eai.module.deployment.build.ArtifactMetaData;

public class DeploymentUtils {
	public static String getCommonToReload(DeploymentInformation deploymentInformation) {
		List<String> foldersToReload = new ArrayList<String>();
		for (ArtifactMetaData meta : deploymentInformation.getBuild().getArtifacts()) {
			String parent = meta.getId().contains(".") ? meta.getId().replaceAll("\\.[^.]+$", "") : null;
			// if it lives on the root, reload it specifically
			if (parent == null) {
				foldersToReload.add(meta.getId());
			}
			else if (!foldersToReload.contains(parent)) {
				foldersToReload.add(parent);
			}
		}
		// it is hard to determine the correct order in which to load the deployed artifacts (there might be artifact repositories etc in there)
		// so currently we just find the longest common parent and reload that entirely
		String common = null;
		boolean rootRefresh = false;
		for (int i = foldersToReload.size() - 1; i >= 0; i--) {
			if (common == null) {
				common = foldersToReload.get(i);
			}
			else if (foldersToReload.get(i).equals(common) || foldersToReload.get(i).startsWith(common + ".")) {
				continue;
			}
			else if (common.startsWith(foldersToReload.get(i))) {
				common = foldersToReload.get(i);
			}
			// find common parent
			else {
				while (common.contains(".")) {
					common = common.replaceAll("\\.[^.]+$", "");
					if (foldersToReload.get(i).equals(common) || foldersToReload.get(i).startsWith(common + ".")) {
						break;
					}
					// if we don't have any parents left and still no match, do a root refresh
					else if (!common.contains(".")) {
						rootRefresh = true;
						break;
					}
				}
			}
			if (rootRefresh) {
				break;
			}
		}
		return rootRefresh ? null : common;
	}
}
