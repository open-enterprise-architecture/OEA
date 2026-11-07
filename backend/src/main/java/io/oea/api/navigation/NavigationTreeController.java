package io.oea.api.navigation;

import io.oea.api.ContractViolationResponse;
import io.oea.api.generated.ViewsNavigationApi;
import io.oea.api.generated.model.CatalogScope;
import io.oea.api.generated.model.TreeNode;
import io.oea.api.generated.model.TreeNodeCreate;
import io.oea.api.generated.model.TreeNodeItem;
import io.oea.api.generated.model.TreeNodeItemCreate;
import io.oea.api.generated.model.TreeNodeRename;
import io.oea.app.navigation.NavigationTree;
import io.oea.app.security.OeaPrincipal;
import io.oea.config.security.OeaAuthentication;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reading the navigation tree and managing its folders and catalog entries (US-054, REQ-050,
 * REQ-094; design D2 of us-054-catalog-navigation-tree). Every write answers with the whole tree
 * as the caller sees it.
 */
@RestController
public class NavigationTreeController implements ViewsNavigationApi {

	private final NavigationTree tree;

	public NavigationTreeController(NavigationTree tree) {
		this.tree = tree;
	}

	@Override
	public ResponseEntity<TreeNode> getExplorerView() {
		return ResponseEntity.ok(node(this.tree.read(principal())));
	}

	@Override
	@ContractViolationResponse(status = 422, title = "Invalid folder", detail = "Some fields are invalid.")
	public ResponseEntity<TreeNode> createTreeNode(TreeNodeCreate request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(node(this.tree.create(principal(), request.getParentId(), request.getName())));
	}

	@Override
	@ContractViolationResponse(status = 422, title = "Invalid folder", detail = "Some fields are invalid.")
	public ResponseEntity<TreeNode> renameTreeNode(UUID nodeId, TreeNodeRename request) {
		return ResponseEntity.ok(node(this.tree.rename(principal(), nodeId, request.getName())));
	}

	@Override
	public ResponseEntity<TreeNode> deleteTreeNode(UUID nodeId) {
		return ResponseEntity.ok(node(this.tree.delete(principal(), nodeId)));
	}

	@Override
	@ContractViolationResponse(status = 422, title = "Invalid entry", detail = "Some fields are invalid.")
	public ResponseEntity<TreeNode> addTreeNodeItem(UUID nodeId, TreeNodeItemCreate request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(node(this.tree.addCatalog(principal(), nodeId, request.getReferenceId(),
					request.getDisplayLabel())));
	}

	@Override
	public ResponseEntity<TreeNode> removeTreeNodeItem(UUID nodeId, UUID itemId) {
		return ResponseEntity.ok(node(this.tree.removeItem(principal(), nodeId, itemId)));
	}

	private static TreeNode node(NavigationTree.Folder folder) {
		return new TreeNode(folder.id(), folder.name(), folder.parentId(), folder.sortOrder(),
				folder.items().stream().map(NavigationTreeController::item).toList(),
				folder.children().stream().map(NavigationTreeController::node).toList());
	}

	private static TreeNodeItem item(NavigationTree.Entry entry) {
		return new TreeNodeItem(entry.id(), TreeNodeItem.ItemTypeEnum.fromValue(entry.itemType()),
				entry.referenceId(), entry.displayLabel(), entry.sortOrder(), entry.name(),
				CatalogScope.fromValue(entry.scope()));
	}

	private static OeaPrincipal principal() {
		return ((OeaAuthentication) SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
	}

}
