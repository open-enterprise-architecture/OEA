package io.oea.api.navigation;

import io.oea.api.ContractViolationResponse;
import io.oea.api.generated.ViewsNavigationApi;
import io.oea.api.generated.model.BrowserCurrentState;
import io.oea.api.generated.model.BrowserItem;
import io.oea.api.generated.model.BrowserItemPage;
import io.oea.api.generated.model.BrowserPlateau;
import io.oea.api.generated.model.BrowserSolution;
import io.oea.api.generated.model.CatalogScope;
import io.oea.api.generated.model.PageMeta;
import io.oea.api.generated.model.PlateauStatus;
import io.oea.api.generated.model.SolutionDeltaType;
import io.oea.api.generated.model.SolutionStatus;
import io.oea.api.generated.model.TreeNode;
import io.oea.api.generated.model.TreeNodeCreate;
import io.oea.api.generated.model.TreeNodeItem;
import io.oea.api.generated.model.TreeNodeItemCreate;
import io.oea.api.generated.model.TreeNodeItemMove;
import io.oea.api.generated.model.TreeNodeMove;
import io.oea.api.generated.model.TreeNodeRename;
import io.oea.app.navigation.Browser;
import io.oea.app.navigation.NavigationTree;
import io.oea.app.security.OeaPrincipal;
import io.oea.config.security.OeaAuthentication;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reading the navigation tree and managing its folders and entries (US-054, US-095 to US-097,
 * REQ-050, REQ-094 to REQ-097; design D2 of us-054-catalog-navigation-tree, D3 and D4 of
 * us-095-navigation-tree-items). Every write answers with the whole tree as the caller sees it.
 */
@RestController
public class NavigationTreeController implements ViewsNavigationApi {

	private final NavigationTree tree;

	private final Browser browser;

	public NavigationTreeController(NavigationTree tree, Browser browser) {
		this.tree = tree;
		this.browser = browser;
	}

	/**
	 * The solution browser (US-132, US-133, REQ-138 to REQ-140; design D3 of
	 * us-132-solution-browser). It sits in this controller because the generated interface of the
	 * tag "Views – Navigation" may have one implementation only.
	 */
	@Override
	public ResponseEntity<io.oea.api.generated.model.Browser> getBrowserView() {
		Browser.Top top = this.browser.top(principal());
		return ResponseEntity.ok(new io.oea.api.generated.model.Browser(
				new BrowserCurrentState(top.currentState().components(), top.currentState().connections(),
						top.currentState().catalogs()),
				top.plateaus()
					.stream()
					.map((plateau) -> new BrowserPlateau(plateau.id(), plateau.name(),
							PlateauStatus.fromValue(plateau.status()),
							plateau.solutions().stream().map(NavigationTreeController::solution).toList()))
					.toList(),
				top.projects().stream().map(NavigationTreeController::solution).toList()));
	}

	@Override
	public ResponseEntity<BrowserItemPage> listBrowserItems(String scope, String category,
			UUID solutionId, Integer parentEntityId, Integer page, Integer pageSize) {
		Browser.ItemPage result = this.browser.items(principal(), scope, solutionId, category, parentEntityId,
				page, pageSize);
		return ResponseEntity.ok(new BrowserItemPage(result.items().stream().map((item) -> new BrowserItem(
				BrowserItem.KindEnum.fromValue(item.kind()), item.id(), item.name(), item.metatypeAbbreviation(),
				item.entityId(), item.solutionId(),
				(item.deltaType() != null) ? SolutionDeltaType.fromValue(item.deltaType()) : null, item.childCount(),
				item.structuringAid(), item.version())).toList(),
				new PageMeta(result.page(), result.pageSize(), Math.toIntExact(result.totalElements()),
						result.totalPages())));
	}

	private static BrowserSolution solution(Browser.SolutionNode node) {
		return new BrowserSolution(node.id(), node.name(), SolutionStatus.fromValue(node.status()),
				node.components());
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
			.body(node(this.tree.addItem(principal(), nodeId,
					new NavigationTree.Placement(request.getItemType().getValue(), request.getReferenceId(),
							request.getEntityId(), request.getDisplayLabel()))));
	}

	@Override
	public ResponseEntity<TreeNode> removeTreeNodeItem(UUID nodeId, UUID itemId) {
		return ResponseEntity.ok(node(this.tree.removeItem(principal(), nodeId, itemId)));
	}

	@Override
	@ContractViolationResponse(status = 422, title = "Invalid move", detail = "Some fields are invalid.")
	public ResponseEntity<TreeNode> moveTreeNode(UUID nodeId, TreeNodeMove request) {
		return ResponseEntity.ok(node(this.tree.moveNode(principal(), nodeId, request.getParentId(),
				request.getPosition())));
	}

	@Override
	@ContractViolationResponse(status = 422, title = "Invalid move", detail = "Some fields are invalid.")
	public ResponseEntity<TreeNode> moveTreeNodeItem(UUID nodeId, UUID itemId, TreeNodeItemMove request) {
		return ResponseEntity.ok(node(this.tree.moveItem(principal(), nodeId, itemId, request.getNodeId(),
				request.getPosition())));
	}

	private static TreeNode node(NavigationTree.Folder folder) {
		return new TreeNode(folder.id(), folder.name(), folder.parentId(), folder.sortOrder(),
				folder.items().stream().map(NavigationTreeController::item).toList(),
				folder.children().stream().map(NavigationTreeController::node).toList());
	}

	private static TreeNodeItem item(NavigationTree.Entry entry) {
		return new TreeNodeItem(entry.id(), TreeNodeItem.ItemTypeEnum.fromValue(entry.itemType()),
				entry.referenceId(), entry.entityId(), entry.displayLabel(), entry.sortOrder(), entry.name(),
				(entry.scope() != null) ? CatalogScope.fromValue(entry.scope()) : null, entry.deleted());
	}

	private static OeaPrincipal principal() {
		return ((OeaAuthentication) SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
	}

}
