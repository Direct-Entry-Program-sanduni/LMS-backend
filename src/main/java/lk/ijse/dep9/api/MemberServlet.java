package lk.ijse.dep9.api;

import com.mysql.cj.jdbc.Driver;
import jakarta.annotation.Resource;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.annotation.JsonbPropertyOrder;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.*;
import lk.ijse.dep9.api.util.HttpServlet2;
import lk.ijse.dep9.db.ConnectionPool;
import lk.ijse.dep9.dto.MemberDTO;
import org.apache.commons.dbcp2.BasicDataSource;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.io.IOException;
import java.lang.management.MemoryMXBean;
import java.sql.*;
import java.util.ArrayList;
import java.util.UUID;
import java.util.jar.JarException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@WebServlet(name = "MemberServlet", value = "/members/*" , loadOnStartup = 0)
public class MemberServlet extends HttpServlet2 {
    @Resource(lookup = "java:/comp/env/jdbc/lms")
    private DataSource pool;


    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getPathInfo() == null || request.getPathInfo().equals("/")) {
            String query = request.getParameter("q");
            String size = request.getParameter("size");
            String page = request.getParameter("page");
            if (query != null && size != null && page != null) {
                if (!size.matches("\\d+") || !page.matches("\\d+")) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "wrong size");
                } else {
                    searchPaginatedMembers(response, query, Integer.parseInt(size), Integer.parseInt(page));
                }
            } else if (query != null) {
                searchMember(response, query);
            } else if (size != null & page != null) {
                if (!size.matches("\\d+") || !page.matches("\\d+")) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "wrong size");
                } else {
                    paginatedAllMembers(response, Integer.parseInt(size), Integer.parseInt(page));

                }

            } else {
                loadAllMembers(response);
            }

        } else {
            Matcher matcher = Pattern.compile("^/([a-zA-Z0-9]{8}(-[a-zA-Z0-9]{4}){3}-[a-zA-Z0-9]{12}/?)$").matcher(request.getPathInfo());
            if (matcher.matches()) {
                getMemberDetails(response, matcher.group(1));
            } else {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "expected valid id");
            }
        }
    }

    private void loadAllMembers(HttpServletResponse response) throws IOException {
        try (Connection connection = pool.getConnection()){
            Statement stm = connection.createStatement();
            ResultSet rst = stm.executeQuery("SELECT * FROM member");
            ArrayList<MemberDTO> members = new ArrayList<>();

            while (rst.next()) {
                String id = rst.getString("id");
                String name = rst.getString("name");
                String address = rst.getString("address");
                String contact = rst.getString("contact");
                MemberDTO dto = new MemberDTO(id, name, address, contact);
                members.add(dto);

            }
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.setContentType("application/json");

                Jsonb jsonb = JsonbBuilder.create();
                jsonb.toJson(members, response.getWriter());


        }catch (SQLException e){
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"Failed to server");
        }

    }

    private void searchMember(HttpServletResponse response,String query) throws IOException {
        try(Connection connection = pool.getConnection()) {
            PreparedStatement stm = connection.prepareStatement("SELECT * FROM member WHERE id LIKE ? OR name LIKE ? OR address LIKE  ? OR contact LIKE ?");
            query = "%"+query+"%";
            stm.setString(1,query);//set parameters
            stm.setString(2,query);
            stm.setString(3,query);
            stm.setString(4,query);

            ResultSet rst = stm.executeQuery();

            ArrayList<MemberDTO> members = new ArrayList<>();

            while(rst.next()) {
                String id = rst.getString("id");
                String name = rst.getString("name");
                String address = rst.getString("address");
                String contact = rst.getString("contact");

                members.add(new MemberDTO(id, name, address, contact));
            }

                Jsonb jsonb = JsonbBuilder.create();
                response.setContentType("application/json");
                jsonb.toJson(members,response.getWriter());

        }catch (SQLException e){
                e.printStackTrace();
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"Failed to load the server");
            }


    }
    private void paginatedAllMembers(HttpServletResponse response,int size,int page) throws IOException {
        try (Connection connection = pool.getConnection()){
            Statement stm = connection.createStatement();
            ResultSet rst = stm.executeQuery("SELECT COUNT(id) AS count FROM member");
            rst.next();
            int totalMember = rst.getInt("count");
            response.setIntHeader("X-Total-Count", totalMember);

            PreparedStatement stm2 = connection.prepareStatement("SELECT * FROM member LIMIT ? OFFSET ?");
            stm2.setInt(1, size);
            stm2.setInt(2, (page-1)*size);
            rst = stm2.executeQuery();

            ArrayList<MemberDTO> members = new ArrayList<>();
            while (rst.next()){
                String id = rst.getString("id");
                String name = rst.getString("name");
                String address = rst.getString("address");
                String contact = rst.getString("contact");
                members.add(new MemberDTO(id, name, address, contact));
            }
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.addHeader("Access-Control-Allow-Headers", "X-Total-Count");
            response.addHeader("Access-Control-Expose-Headers", "X-Total-Count");
            response.setContentType("application/json");
            JsonbBuilder.create().toJson(members, response.getWriter());

        }catch (SQLException e){
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"Failed to load the server");
        }

    }

    private void getMemberDetails(HttpServletResponse response,String id) throws IOException {
        try (Connection connection = pool.getConnection()){
            PreparedStatement stm = connection.prepareStatement("SELECT * FROM member WHERE id =?");
            stm.setString(1, id);
            ResultSet rst = stm.executeQuery();
            if (rst.next()){
                String name = rst.getString("name");
                String address = rst.getString("address");
                String contact = rst.getString("contact");
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }


    }
    private void searchPaginatedMembers(HttpServletResponse response,String query,int size,int page) throws IOException {
        try (Connection connection = pool.getConnection()) {
            PreparedStatement stmCount = connection.prepareStatement("SELECT COUNT(id) FROM member WHERE id LIKE ? OR name LIKE ? OR address LIKE ? OR contact LIKE ?");
            PreparedStatement stm = connection.prepareStatement("SELECT * FROM member WHERE id LIKE ? OR name LIKE ? OR address LIKE ? OR contact LIKE ? LIMIT ? OFFSET ?");

            query = "%" + query + "%";
            stmCount.setString(1, query);
            stmCount.setString(2, query);
            stmCount.setString(3, query);
            stmCount.setString(4, query);
            ResultSet rst = stmCount.executeQuery();
            rst.next();

            int totalMembers = rst.getInt(1);
            response.addIntHeader("X-Total-Count", totalMembers);

            stm.setString(1, query);
            stm.setString(2, query);
            stm.setString(3, query);
            stm.setString(4, query);
            stm.setInt(5, size);
            stm.setInt(6, (page - 1) * size);

            ResultSet rst2 = stm.executeQuery();

            ArrayList<MemberDTO> members = new ArrayList<>();

            while (rst2.next()) {
                String id = rst2.getString("id");
                String name = rst2.getString("name");
                String address = rst2.getString("address");
                String contact = rst2.getString("contact");
                MemberDTO dto = new MemberDTO(id, name, address, contact);
                members.add(dto);
            }

            response.addHeader("Access-Control-Allow-Origin", "*");
            response.addHeader("Access-Control-Allow-Headers", "X-Total-Count");
            response.addHeader("Access-Control-Expose-Headers", "X-Total-Count");
            response.setContentType("application/json");
            JsonbBuilder.create().toJson(members, response.getWriter());
        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to fetch members");
        }
    }


    @Override
   protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//       if(request.getPathInfo()==null || request.getPathInfo().equals("/")){
//           try {
//               if (!request.getContentType().startsWith("application/json")){
//                   throw new JsonbException("Invalid Json");
//               }
//              MemberDTO member =  JsonbBuilder.create().fromJson(request.getReader(), MemberDTO.class);
//               if (member.getName() == null || !member.getName().matches("[A-Za-z]+")){
//                   throw new JsonbException("Name is Empty or Invalid");
//               } else if (member.getContact() == null || !member.getContact().matches("\\d{3}-\\{7}")) {
//                   throw new JsonbException("Contact is Empty or Invalid");
//               } else if (member.getAddress() == null || !member.getAddress().matches("[A-Za-z0-9,.:;/\\-]+")) {
//                   throw new JsonbException("Address is Empty or Invalid");
//               }
//               try(Connection connection = pool.getConnection()){
//                   member.setId(UUID.randomUUID().toString());
//                   PreparedStatement stm = connection.prepareStatement("INSERT INTO member(id, name, address, contact) VALUES ");
//                   stm.setString(1,member.getId());
//                   stm.setString(2,member.getName());
//                   stm.setString(3,member.getAddress());
//                   stm.setString(4,member.getContact());
//
//                   int affectedRows = stm.executeUpdate();
//                   if (affectedRows == 1){
//                       response.setStatus(HttpServletResponse.SC_CREATED);
//                       response.setContentType("application/json");
//                       JsonbBuilder.create()
//                   }
//
//               } catch (SQLException e) {
//                   throw new RuntimeException(e);
//               }
//           }catch (JarException e){
//               response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON");
//           }
//
//       }else {
//           response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
//       }
 }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (req.getPathInfo() == null || req.getPathInfo().equals("/")){
            resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
            return;
        }

        Matcher matcher = Pattern.compile("^/([a-zA-Z0-9]{8}(-[a-zA-Z0-9]{4}){3}-[a-zA-Z0-9]{12}/?)$").matcher(req.getPathInfo());
        if (matcher.matches()) {
            deleteMember(matcher.group(1), resp);
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
        }
    }
    private void deleteMember(String memberId, HttpServletResponse response){
        try (Connection connection = pool.getConnection()){
            PreparedStatement stm = connection.prepareStatement("DELETE FROM member WHERE id=?");
            stm.setString(1,memberId);
            int affectedRows = stm.executeUpdate();
            if (affectedRows == 0){
                response.sendError(HttpServletResponse.SC_FOUND,"Invalid member id");
            }else {
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }

        } catch (SQLException |IOException e) {
            throw new RuntimeException(e);

        }
    }

    @Override
    protected void doPatch(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {
        if (req.getPathInfo() == null || req.getPathInfo().equals("/")){
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
            return;
        }
        Matcher matcher = Pattern.compile("^/([A-Fa-f0-9]{8}(-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12}/?$)").matcher(req.getPathInfo());
        if (matcher.matches()){
            updateMember(matcher.group(1),req,response );
        }else {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
        }
    }
    private void updateMember(String memberID, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            if (request.getContentType()==null || !request.getContentType().startsWith("application/json")){
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,"Invalid JSON");
                return;
            }

            MemberDTO member = JsonbBuilder.create().fromJson(request.getReader(), MemberDTO.class);
            if (member.getId()==null || !memberID.equalsIgnoreCase(member.getId())){
                throw new JsonbException("Id id empty or invalid");
            } else if (member.getName()==null || !member.getName().matches("[A-Za-z ]+")){
                throw new JsonbException("Name is empty or Invalid");
            } else if (member.getContact()==null || !member.getContact().matches("\\d{3}-\\d{7}")) {
                throw new JsonbException("Contact is empty of Invalid");
            } else if (member.getAddress()==null || !member.getAddress().matches("[A-Za-z0-9,:./\\-]+")) {
                throw new JsonbException("Address is empty or Invalid");
            }

            try(Connection connection = pool.getConnection()) {
                PreparedStatement stm = connection.prepareStatement("UPDATE member SET name =? , address =? , contact =? WHERE id =?");

                stm.setString(1,member.getName());
                stm.setString(2,member.getAddress());
                stm.setString(3,member.getContact());
                stm.setString(4,member.getId());

                if (stm.executeUpdate()==1){
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                }else {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND,"Member does not exists");
                }
            } catch (SQLException e) {
                e.printStackTrace();
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"Failed to update the member");
            }
        } catch (JsonbException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,e.getMessage());
        }
    }
}


