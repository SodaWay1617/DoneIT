package com.doneit.project.application;

import com.doneit.project.domain.Project;
import com.doneit.project.domain.TaskVisibilityMode;
import java.util.Set;
import java.util.HashSet;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.doneit.user.domain.User;
import com.doneit.user.domain.UserRepository;
import com.doneit.user.infrastructure.persistence.UserRowMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true)
public class ProjectService {
 private final JdbcTemplate db; private final UserRepository users; private final UserRowMapper rows;
 public ProjectService(JdbcTemplate db,UserRepository users,UserRowMapper rows){this.db=db;this.users=users;this.rows=rows;}
 public User user(){return users.findByLogin(SecurityContextHolder.getContext().getAuthentication().getName()).orElseThrow();}
 public List<Project> list(){Long u=user().id();return db.query(\u0022SELECT p.*,p.owner_user_id=? owner FROM projects p JOIN project_members m ON m.project_id=p.id WHERE m.user_id=? ORDER BY p.default_project DESC,p.name\u0022,ProjectService::map,u,u);}
 public Project get(Long id){Long u=user().id();return db.query(\u0022SELECT p.*,p.owner_user_id=? owner FROM projects p JOIN project_members m ON m.project_id=p.id WHERE p.id=? AND m.user_id=?\u0022,ProjectService::map,u,id,u).stream().findFirst().orElseThrow();}
 public boolean canAccess(Long id){return id==null||list().stream().anyMatch(p->p.id().equals(id));}
 @Transactional public void create(String n,String c,String d){create(n,c,d,TaskVisibilityMode.ALL_MEMBERS);}
 @Transactional public void create(String n,String c,String d,TaskVisibilityMode visibility){User u=user();LocalDateTime x=LocalDateTime.now();Long id=db.queryForObject("INSERT INTO projects(name,code,description,owner_user_id,created_at,updated_at,task_visibility) VALUES(?,?,?,?,?,?,?) RETURNING id",Long.class,req(n),code(c),d,u.id(),x,x,visibility.name());db.update("INSERT INTO project_members VALUES(?,?,?)",id,u.id(),x);}
 @Transactional public void update(Long id,String n,String c,String d){update(id,n,c,d,get(id).taskVisibility());}
 @Transactional public void update(Long id,String n,String c,String d,TaskVisibilityMode visibility){if(db.update("UPDATE projects SET name=?,code=?,description=?,task_visibility=?,updated_at=? WHERE id=? AND owner_user_id=? AND NOT default_project",req(n),code(c),d,visibility.name(),LocalDateTime.now(),id,user().id())==0)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only the owner can edit a non-default project");}
 @Transactional public void delete(Long id){if(db.update(\u0022DELETE FROM projects WHERE id=? AND owner_user_id=? AND NOT default_project\u0022,id,user().id())==0)throw new IllegalArgumentException();}
 public List<User> members(Long id){get(id);return db.query(\u0022SELECT u.id,u.login,u.password_hash,u.display_name,u.created_at,u.updated_at,u.show_project_in_task_title FROM users u JOIN project_members m ON m.user_id=u.id WHERE m.project_id=? ORDER BY u.login\u0022,rows,id);}
 public List<User> available(Long id){return get(id).owner()?db.query(\u0022SELECT u.id,u.login,u.password_hash,u.display_name,u.created_at,u.updated_at,u.show_project_in_task_title FROM users u WHERE NOT EXISTS(SELECT 1 FROM project_members m WHERE m.project_id=? AND m.user_id=u.id) ORDER BY u.login\u0022,rows,id):List.of();}
 @Transactional public void add(Long id,Long uid){db.update(\u0022INSERT INTO project_members SELECT id,?,? FROM projects WHERE id=? AND owner_user_id=? AND NOT default_project ON CONFLICT DO NOTHING\u0022,uid,LocalDateTime.now(),id,user().id());}
 @Transactional public void remove(Long id,Long uid){db.update(\u0022DELETE FROM project_members m USING projects p WHERE m.project_id=p.id AND p.id=? AND p.owner_user_id=? AND m.user_id=? AND m.user_id<>p.owner_user_id\u0022,id,user().id(),uid);}
 @Transactional public void leave(Long id){Long u=user().id();db.update(\u0022DELETE FROM project_members m USING projects p WHERE m.project_id=p.id AND p.id=? AND m.user_id=? AND p.owner_user_id<>? AND NOT p.default_project\u0022,id,u,u);}
 public Long requireTaskAccess(Long id,boolean regular){
  String table=regular?"regular_tasks":"tasks";
  return db.query("SELECT t.project_id FROM "+table+" t WHERE t.id=? AND "+TaskVisibility.memberTasks("t",regular),
   (rs,row)->rs.getLong(1),id,user().id()).stream().findFirst()
   .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Task not found"));
 }
 public Set<Long> personalTaskIds(boolean regular){
  return taskIds(regular,true);
 }
 public Set<Long> accessibleTaskIds(boolean regular){
  return taskIds(regular,false);
 }
 private Set<Long> taskIds(boolean regular,boolean personal){
  String table=regular?"regular_tasks":"tasks";
  return new HashSet<>(db.query("SELECT t.id FROM "+table+" t WHERE "+TaskVisibility.memberTasks("t",regular,personal),
   (rs,row)->rs.getLong(1),user().id()));
 }
 private static Project map(java.sql.ResultSet r,int n)throws java.sql.SQLException{return new Project(r.getLong("id"),r.getString("name"),r.getString("code"),r.getString("description"),r.getLong("owner_user_id"),r.getBoolean("default_project"),r.getBoolean("owner"),TaskVisibilityMode.valueOf(r.getString("task_visibility")));}
 private static String req(String v){if(v==null||v.isBlank())throw new IllegalArgumentException();return v.trim();}
 private static String code(String v){return req(v).toUpperCase().replaceAll(\u0022[^A-Z0-9_-]\u0022,\u0022-\u0022);}
}
