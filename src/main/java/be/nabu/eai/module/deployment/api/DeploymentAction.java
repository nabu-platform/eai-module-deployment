package be.nabu.eai.module.deployment.api;

import java.util.List;

import be.nabu.libs.validator.api.ValidationMessage;

// each deployment action is a service that can optionally take input
// if the service has input, we create a new service with 1 map step (see workflow)
// the output of the generated service is in the input of the deployment service
// the generated service is run during deployment preparation and the output is captured in the deployment zip
// after deployment, the target server is responsible for running the deployment service with the captured input
// can use this do synchronize data in the database, synchronize ddls, run complex alterations...
/** Postgresql
 * 
 * automatic alters for each table:
 * can use this with a general select to see which alters need to be run
 * 
SELECT table_name, column_name, 'alter ' || table_name || ' add column ' || column_name || ' ' || data_type || (case when is_nullable = 'YES' then '' else ' not null' end) || (case when column_default is null then '' else ' default ' || column_default end) || ';' as alter
FROM information_schema.columns
where table_schema not in ('pg_catalog', 'information_schema')
 */
public interface DeploymentAction {
	public List<ValidationMessage> act();
}
