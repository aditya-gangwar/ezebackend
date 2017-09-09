package in.ezeshop.database;

import com.backendless.Backendless;
import com.backendless.BackendlessCollection;
import com.backendless.async.callback.AsyncCallback;
import com.backendless.persistence.BackendlessDataQuery;
import in.ezeshop.common.database.Future;

public class MycKeys
{
  private String ownerId;
  private java.util.Date updated;
  private String key;
  private String objectId;
  private String name;
  private java.util.Date created;
  public String getOwnerId()
  {
    return ownerId;
  }

  public java.util.Date getUpdated()
  {
    return updated;
  }

  public String getKey()
  {
    return key;
  }

  public void setKey( String key )
  {
    this.key = key;
  }

  public String getObjectId()
  {
    return objectId;
  }

  public String getName()
  {
    return name;
  }

  public void setName( String name )
  {
    this.name = name;
  }

  public java.util.Date getCreated()
  {
    return created;
  }

                                                    
  public MycKeys save()
  {
    return Backendless.Data.of( MycKeys.class ).save( this );
  }

  public Future<MycKeys> saveAsync()
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<MycKeys> future = new Future<MycKeys>();
      Backendless.Data.of( MycKeys.class ).save( this, future );

      return future;
    }
  }

  public void saveAsync( AsyncCallback<MycKeys> callback )
  {
    Backendless.Data.of( MycKeys.class ).save( this, callback );
  }

  public Long remove()
  {
    return Backendless.Data.of( MycKeys.class ).remove( this );
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
      Backendless.Data.of( MycKeys.class ).remove( this, future );

      return future;
    }
  }

  public void removeAsync( AsyncCallback<Long> callback )
  {
    Backendless.Data.of( MycKeys.class ).remove( this, callback );
  }

  public static MycKeys findById( String id )
  {
    return Backendless.Data.of( MycKeys.class ).findById( id );
  }

  public static Future<MycKeys> findByIdAsync( String id )
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<MycKeys> future = new Future<MycKeys>();
      Backendless.Data.of( MycKeys.class ).findById( id, future );

      return future;
    }
  }

  public static void findByIdAsync( String id, AsyncCallback<MycKeys> callback )
  {
    Backendless.Data.of( MycKeys.class ).findById( id, callback );
  }

  public static MycKeys findFirst()
  {
    return Backendless.Data.of( MycKeys.class ).findFirst();
  }

  public static Future<MycKeys> findFirstAsync()
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<MycKeys> future = new Future<MycKeys>();
      Backendless.Data.of( MycKeys.class ).findFirst( future );

      return future;
    }
  }

  public static void findFirstAsync( AsyncCallback<MycKeys> callback )
  {
    Backendless.Data.of( MycKeys.class ).findFirst( callback );
  }

  public static MycKeys findLast()
  {
    return Backendless.Data.of( MycKeys.class ).findLast();
  }

  public static Future<MycKeys> findLastAsync()
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<MycKeys> future = new Future<MycKeys>();
      Backendless.Data.of( MycKeys.class ).findLast( future );

      return future;
    }
  }

  public static void findLastAsync( AsyncCallback<MycKeys> callback )
  {
    Backendless.Data.of( MycKeys.class ).findLast( callback );
  }

  public static BackendlessCollection<MycKeys> find( BackendlessDataQuery query )
  {
    return Backendless.Data.of( MycKeys.class ).find( query );
  }

  public static Future<BackendlessCollection<MycKeys>> findAsync( BackendlessDataQuery query )
  {
    if( Backendless.isAndroid() )
    {
      throw new UnsupportedOperationException( "Using this method is restricted in Android" );
    }
    else
    {
      Future<BackendlessCollection<MycKeys>> future = new Future<BackendlessCollection<MycKeys>>();
      Backendless.Data.of( MycKeys.class ).find( query, future );

      return future;
    }
  }

  public static void findAsync( BackendlessDataQuery query, AsyncCallback<BackendlessCollection<MycKeys>> callback )
  {
    Backendless.Data.of( MycKeys.class ).find( query, callback );
  }
}