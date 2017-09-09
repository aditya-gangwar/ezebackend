package in.ezeshop.database;

import com.backendless.Backendless;
import com.backendless.BackendlessCollection;
import com.backendless.async.callback.AsyncCallback;
import com.backendless.persistence.BackendlessDataQuery;
import in.ezeshop.common.database.Future;

public class InternalUser
{
  private java.util.Date updated;
  private String mobile_num;
  private String status_reason;
  private String objectId;
  private java.util.Date created;
  private String name;
  private String id;
  private Integer admin_status;
  private String dob;
  private String ownerId;
  private String admin_remarks;
  private Boolean first_login_ok;
  private Boolean debugLogs;

  public Boolean getDebugLogs() {
    return debugLogs;
  }

  public void setDebugLogs(Boolean debugLogs) {
    this.debugLogs = debugLogs;
  }

  public Boolean getFirst_login_ok() {
    return first_login_ok;
  }

  public void setFirst_login_ok(Boolean first_login_ok) {
    this.first_login_ok = first_login_ok;
  }

  public java.util.Date getUpdated()
  {
    return updated;
  }

  public String getMobile_num()
  {
    return mobile_num;
  }

  public void setMobile_num( String mobile_num )
  {
    this.mobile_num = mobile_num;
  }

  public String getStatus_reason()
  {
    return status_reason;
  }

  public void setStatus_reason( String status_reason )
  {
    this.status_reason = status_reason;
  }

  public String getObjectId()
  {
    return objectId;
  }

  public java.util.Date getCreated()
  {
    return created;
  }

  public String getName()
  {
    return name;
  }

  public void setName( String name )
  {
    this.name = name;
  }

  public String getId()
  {
    return id;
  }

  public void setId( String id )
  {
    this.id = id;
  }

  public Integer getAdmin_status()
  {
    return admin_status;
  }

  public void setAdmin_status( Integer admin_status )
  {
    this.admin_status = admin_status;
  }

  public String getDob()
  {
    return dob;
  }

  public void setDob( String dob )
  {
    this.dob = dob;
  }

  public String getOwnerId()
  {
    return ownerId;
  }

  public String getAdmin_remarks()
  {
    return admin_remarks;
  }

  public void setAdmin_remarks( String admin_remarks )
  {
    this.admin_remarks = admin_remarks;
  }

                                                    
  public InternalUser save()
  {
    return Backendless.Data.of( InternalUser.class ).save( this );
  }

  public Future<InternalUser> saveAsync()
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<InternalUser> future = new Future<InternalUser>();
      Backendless.Data.of( InternalUser.class ).save( this, future );

      return future;
    }
  }

  public void saveAsync( AsyncCallback<InternalUser> callback )
  {
    Backendless.Data.of( InternalUser.class ).save( this, callback );
  }

  public Long remove()
  {
    return Backendless.Data.of( InternalUser.class ).remove( this );
  }

  public Future<Long> removeAsync()
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<Long> future = new Future<Long>();
      Backendless.Data.of( InternalUser.class ).remove( this, future );

      return future;
    }
  }

  public void removeAsync( AsyncCallback<Long> callback )
  {
    Backendless.Data.of( InternalUser.class ).remove( this, callback );
  }

  public static InternalUser findById(String id )
  {
    return Backendless.Data.of( InternalUser.class ).findById( id );
  }

  public static Future<InternalUser> findByIdAsync(String id )
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<InternalUser> future = new Future<InternalUser>();
      Backendless.Data.of( InternalUser.class ).findById( id, future );

      return future;
    }
  }

  public static void findByIdAsync( String id, AsyncCallback<InternalUser> callback )
  {
    Backendless.Data.of( InternalUser.class ).findById( id, callback );
  }

  public static InternalUser findFirst()
  {
    return Backendless.Data.of( InternalUser.class ).findFirst();
  }

  public static Future<InternalUser> findFirstAsync()
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<InternalUser> future = new Future<InternalUser>();
      Backendless.Data.of( InternalUser.class ).findFirst( future );

      return future;
    }
  }

  public static void findFirstAsync( AsyncCallback<InternalUser> callback )
  {
    Backendless.Data.of( InternalUser.class ).findFirst( callback );
  }

  public static InternalUser findLast()
  {
    return Backendless.Data.of( InternalUser.class ).findLast();
  }

  public static Future<InternalUser> findLastAsync()
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<InternalUser> future = new Future<InternalUser>();
      Backendless.Data.of( InternalUser.class ).findLast( future );

      return future;
    }
  }

  public static void findLastAsync( AsyncCallback<InternalUser> callback )
  {
    Backendless.Data.of( InternalUser.class ).findLast( callback );
  }

  public static BackendlessCollection<InternalUser> find(BackendlessDataQuery query )
  {
    return Backendless.Data.of( InternalUser.class ).find( query );
  }

  public static Future<BackendlessCollection<InternalUser>> findAsync(BackendlessDataQuery query )
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<BackendlessCollection<InternalUser>> future = new Future<BackendlessCollection<InternalUser>>();
      Backendless.Data.of( InternalUser.class ).find( query, future );

      return future;
    }
  }

  public static void findAsync( BackendlessDataQuery query, AsyncCallback<BackendlessCollection<InternalUser>> callback )
  {
    Backendless.Data.of( InternalUser.class ).find( query, callback );
  }
}