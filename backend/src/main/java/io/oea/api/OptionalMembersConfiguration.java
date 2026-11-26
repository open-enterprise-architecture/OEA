package io.oea.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.oea.api.generated.model.CatalogRow;
import io.oea.api.generated.model.DeletedEntity;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Leaves out the optional members that are not set in the responses where the contract says
 * "absent" (design D1 and D2 of s17-deleted-entities): the deletion members of a catalog row
 * (set only on deleted rows of a query with {@code includeDeleted}) and the endpoints and actor of a
 * deleted entity. The generated models would write them as {@code null}; only these properties are
 * changed, the other responses keep their form.
 */
@Configuration(proxyBeanMethods = false)
public class OptionalMembersConfiguration {

	@Bean
	JsonMapperBuilderCustomizer optionalMembersAreAbsent() {
		return (builder) -> builder.addMixIn(CatalogRow.class, CatalogRowMixin.class)
			.addMixIn(DeletedEntity.class, DeletedEntityMixin.class);
	}

	/** The deletion members of a catalog row. */
	abstract static class CatalogRowMixin {

		@JsonInclude(JsonInclude.Include.NON_NULL)
		abstract Boolean getDeleted();

		@JsonInclude(JsonInclude.Include.NON_NULL)
		abstract Object getDeletedAt();

		@JsonInclude(JsonInclude.Include.NON_NULL)
		abstract Integer getVersion();

	}

	/** The optional members of a deleted entity; {@code deletedWith} stays, as {@code null} for an entity deleted on its own. */
	abstract static class DeletedEntityMixin {

		@JsonInclude(JsonInclude.Include.NON_NULL)
		abstract Object getSource();

		@JsonInclude(JsonInclude.Include.NON_NULL)
		abstract Object getTarget();

		@JsonInclude(JsonInclude.Include.NON_NULL)
		abstract Object getDeletedBy();

	}

}
