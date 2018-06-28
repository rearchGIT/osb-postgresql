/**
 * 
 */
package de.evoila.cf.broker.custom.postgres;

import de.evoila.cf.broker.bean.ExistingEndpointBean;
import de.evoila.cf.broker.model.Plan;
import de.evoila.cf.broker.model.Platform;
import de.evoila.cf.broker.model.ServerAddress;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.util.ServiceInstanceUtils;
import de.evoila.cf.cpi.bosh.PostgresBoshPlatformService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * @author Johannes Hiemer.
 *
 */
@Service
public class PostgresCustomImplementation {

    private Logger log = LoggerFactory.getLogger(PostgresCustomImplementation.class);

    @Autowired
    private ExistingEndpointBean existingEndpointBean;

    @Autowired
    private PostgresBoshPlatformService postgresBoshPlatformService;

	public void setUpBindingUserPrivileges(PostgresDbService jdbcService, String username) throws SQLException {
		jdbcService.executeUpdate("ALTER DEFAULT PRIVILEGES FOR ROLE \"" + username +"\" IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO \""+ username + "\"");
		jdbcService.executeUpdate("ALTER DEFAULT PRIVILEGES FOR ROLE \"" + username +"\" IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO \""+ username + "\"");
		jdbcService.executeUpdate("ALTER DEFAULT PRIVILEGES FOR ROLE \"" + username +"\" IN SCHEMA public GRANT ALL PRIVILEGES ON FUNCTIONS TO \""+ username + "\"");
		jdbcService.executeUpdate("ALTER DEFAULT PRIVILEGES FOR ROLE \"" + username +"\" IN SCHEMA public GRANT ALL PRIVILEGES ON TYPES TO \""+ username + "\"");
	}
	
	public void bindRoleToDatabase(PostgresDbService jdbcService, String username, String password, String database, boolean isAdmin)
			throws SQLException {

		if (isAdmin){
			jdbcService.executeUpdate("CREATE ROLE \"" + username + "\" WITH LOGIN password '" + password + "'");
		} else {
			jdbcService.executeUpdate("CREATE ROLE \"" + username + "\" WITH INHERIT LOGIN password '" + password + "'");
		}

		if (isAdmin){
			jdbcService.executeUpdate("ALTER DATABASE \"" + database + "\" OWNER TO \"" + username + "\"");
			jdbcService.executeUpdate("GRANT ALL PRIVILEGES ON SCHEMA public TO \""+ username + "\"");
			jdbcService.executeUpdate("GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO \""+ username + "\"");
			jdbcService.executeUpdate("GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public To \""+ username + "\"");
			jdbcService.executeUpdate("GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public To \""+ username + "\"");
			
		} else {
			jdbcService.executeUpdate("GRANT CONNECT ON DATABASE \"" + database + "\" TO \"" + username + "\"");
		}
	}

	public void unbindRoleFromDatabase(ServiceInstance serviceInstance, Plan plan,PostgresDbService jdbcService, String roleName, String fallBackRoleName) throws SQLException {
		//		jdbcService.executeUpdate("REVOKE \""+ fallBackRoleName + "\" FROM \"" + roleName + "\"")

		jdbcService.executeUpdate("CREATE ROLE \"" + fallBackRoleName + "\" NOLOGIN");

		Map<String, String> databases = jdbcService.executeSelect("SELECT datname FROM pg_database WHERE datistemplate = false", "datname");
		for(Map.Entry<String, String> database : databases.entrySet()) {

			PostgresDbService jdbcService_tmp = this.connection(serviceInstance, plan,database.getValue());

			jdbcService_tmp.executeUpdate("REASSIGN OWNED BY \"" + roleName + "\" TO \"" + fallBackRoleName + "\"");
			jdbcService_tmp.executeUpdate("DROP OWNED BY \"" + roleName + "\"");
			jdbcService_tmp.executeUpdate("REVOKE ALL ON SCHEMA public FROM \"" + roleName + "\"");

			jdbcService_tmp.closeIfConnected();
		}

		jdbcService.executeUpdate("DROP ROLE \"" + roleName + "\"");
	}

	public List<ServerAddress> filterServerAddresses (ServiceInstance serviceInstance, Plan plan) {
		List<ServerAddress> serverAddresses = serviceInstance.getHosts();
		String ingressInstanceGroup = plan.getMetadata().getIngressInstanceGroup();
		if (ingressInstanceGroup != null && ingressInstanceGroup.length() > 0) {
			serverAddresses = ServiceInstanceUtils.filteredServerAddress(serviceInstance.getHosts(),ingressInstanceGroup);
		}
		return serverAddresses;
	}

    public PostgresDbService connection(ServiceInstance serviceInstance, Plan plan, String database) {
        List<ServerAddress> serverAddresses=serviceInstance.getHosts();

		String username="";
        String password="";

        if(plan.getPlatform() == Platform.BOSH) {
            username=serviceInstance.getUsername();
            password=serviceInstance.getPassword();
            if(database==null) {
				database = "admin";
			}
			serverAddresses = filterServerAddresses(serviceInstance,plan);

		} else if (plan.getPlatform() == Platform.EXISTING_SERVICE) {
			username=existingEndpointBean.getUsername();
			password=existingEndpointBean.getPassword();
			database=existingEndpointBean.getDatabase();
        }

		PostgresDbService jdbcService = new PostgresDbService();
		jdbcService.createConnection(
				username,
				password,
				database,
				serverAddresses);

        return jdbcService;
    }

	public PostgresDbService connection(ServiceInstance serviceInstance, Plan plan) {
		return connection(serviceInstance,plan,null);
	}
}
