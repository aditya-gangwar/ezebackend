package in.ezeshop.database;

import com.backendless.Backendless;
import com.backendless.BackendlessCollection;
import com.backendless.async.callback.AsyncCallback;
import com.backendless.persistence.BackendlessDataQuery;
import in.ezeshop.common.database.Future;

public class InternalUserDevice
{
  private String ownerId;
  private java.util.Date created;
  private String objectId;
  private String userId;
  private String tempId;
  private java.util.Date updated;
  private String instanceId;
  public String getOwnerId()
  {
    return ownerId;
  }

  public java.util.Date getCreated()
  {
    return created;
  }

  public String getObjectId()
  {
    return objectId;
  }

  public String getUserId()
  {
    return userId;
  }

  public void setUserId( String userId )
  {
    this.userId = userId;
  }

  public String getTempId()
  {
    return tempId;
  }

  public void setTempId( String tempId )
  {
    this.tempId = tempId;
  }

  public java.util.Date getUpdated()
  {
    return updated;
  }

  public String getInstanceId()
  {
    return instanceId;
  }

  public void setInstanceId( String instanceId )
  {
    this.instanceId = instanceId;
  }

                                                    
  public InternalUserDevice save()
  {
    return Backendless.Data.of( InternalUserDevice.class ).save( this );
  }

  public Future<InternalUserDevice> saveAsync()
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<InternalUserDevice> future = new Future<InternalUserDevice>();
      Backendless.Data.of( InternalUserDevice.class ).save( this, future );

      return future;
    }
  }

  public void saveAsync( AsyncCallback<InternalUserDevice> callback )
  {
    Backendless.Data.of( InternalUserDevice.class ).save( this, callback );
  }

  public Long remove()
  {
    return Backendless.Data.of( InternalUserDevice.class ).remove( this );
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
      Backendless.Data.of( InternalUserDevice.class ).remove( this, future );

      return future;
    }
  }

  public void removeAsync( AsyncCallback<Long> callback )
  {
    Backendless.Data.of( InternalUserDevice.class ).remove( this, callback );
  }

  public static InternalUserDevice findById( String id )
  {
    return Backendless.Data.of( InternalUserDevice.class ).findById( id );
  }

  public static Future<InternalUserDevice> findByIdAsync( String id )
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<InternalUserDevice> future = new Future<InternalUserDevice>();
      Backendless.Data.of( InternalUserDevice.class ).findById( id, future );

      return future;
    }
  }

  public static void findByIdAsync( String id, AsyncCallback<InternalUserDevice> callback )
  {
    Backendless.Data.of( InternalUserDevice.class ).findById( id, callback );
  }

  public static InternalUserDevice findFirst()
  {
    return Backendless.Data.of( InternalUserDevice.class ).findFirst();
  }

  public static Future<InternalUserDevice> findFirstAsync()
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<InternalUserDevice> future = new Future<InternalUserDevice>();
      Backendless.Data.of( InternalUserDevice.class ).findFirst( future );

      return future;
    }
  }

  public static void findFirstAsync( AsyncCallback<InternalUserDevice> callback )
  {
    Backendless.Data.of( InternalUserDevice.class ).findFirst( callback );
  }

  public static InternalUserDevice findLast()
  {
    return Backendless.Data.of( InternalUserDevice.class ).findLast();
  }

  public static Future<InternalUserDevice> findLastAsync()
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<InternalUserDevice> future = new Future<InternalUserDevice>();
      Backendless.Data.of( InternalUserDevice.class ).findLast( future );

      return future;
    }
  }

  public static void findLastAsync( AsyncCallback<InternalUserDevice> callback )
  {
    Backendless.Data.of( InternalUserDevice.class ).findLast( callback );
  }

  public static BackendlessCollection<InternalUserDevice> find( BackendlessDataQuery query )
  {
    return Backendless.Data.of( InternalUserDevice.class ).find( query );
  }

  public static Future<BackendlessCollection<InternalUserDevice>> findAsync( BackendlessDataQuery query )
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<BackendlessCollection<InternalUserDevice>> future = new Future<BackendlessCollection<InternalUserDevice>>();
      Backendless.Data.of( InternalUserDevice.class ).find( query, future );

      return future;
    }
  }

  public static void findAsync( BackendlessDataQuery query, AsyncCallback<BackendlessCollection<InternalUserDevice>> callback )
  {
    Backendless.Data.of( InternalUserDevice.class ).find( query, callback );
  }
}