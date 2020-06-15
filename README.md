# eXo Office Online add-on

The add-on provides integration with Office Online and allows users to view and edit Word, Excel and Powerpoint documents in eXo Platform using Office editors. 
In addition, users can edit documents collaboratively with other users using Office. In order to edit documents, users require an Office license.


## Usage

After installation Office Online add-on new editing options should appear in the document preview and Explorer. Also, ‘New Document’ menu will be extended with Office templates for new documents.

When user clicks ‘Edit in Office Online’ (or Edit) the document will be loaded in separate editor page. It could take a few seconds, depends on the file size.
If user is not signed in Office 365 they will be redirected to the login page.
Once signed in, Office for the web will verify that the user has a valid Office 365 subscription. After this is verified, Office for the web will automatically redirect the user back to the editor.

The document gets uploaded to temporary storage on Microsoft side, and the content displayed in the editor frame.

While editing, the changes are saved to the MS storage, and periodically sync with eXo side. When the updated content is saved to eXo, users can see a yellow refresh banner on the document preview that allows to update the preview.

Each save to eXo creates a new version, except when the version accumulation went off.
Autosave frequency and version owner depend on the document format. 
Summary of co-authoring behavior for Office editors:

![Save frequency](/docs/images/save-frequency.png)

If the user doesn’t have permissions for editing document, it could be opened in view mode.
Also, Office Online provides a way to create new documents, so users are able to create new documents based on templates provided in ‘New Document’ popup in Explorer.


## Integration Schema
We use Web Application Open Platform Interface (WOPI) protocol to integrate Office for the web with eXo Platform. The WOPI protocol enables Office for the web to access and change files that are stored in eXo.
Editing/viewing documents in Office Online typically consists of such steps:
1. User clicks 'Edit Online' button in ECMS/Stream/Preview and the HTML page with editor frame is loaded to their browser.
2. Editor frame makes requests to WOPI Client (Word/Excel/PowerPoint) on MS side, and passes the WOPI Host URL. This URL will be used by WOPI Client to interact with our side and the domain must be present in the white list of Microsoft Cloud Storage Partner Program. We're using a proxy for this purpose that redirects all WOPI requests to the server. 
3. WOPI Client requests file info, file content from WOPI Host using the proxy.
4. User edits the document in the editor frame, the data is tracked by WOPI client, and when the editing is finished or the editor is closed, WOPI client sends putFile request to WOPI Host (using Proxy) 
5. The document is stored on eXo side. The diagram shows the schema of the integration Office Online with eXo Platform.

![Integration Schema](/docs/images/integration-shema.png)
Office for the web only makes WOPI requests to trusted partner domains. This domain list is called the WOPI domain allow list. The domain should be whitelisted. We’re using a proxy with allowed domain that redirects to eXo Plaform server (wopi endpoints).

## Installation & Configuration
Before add-on installation, you have to make sure that the proxy is configured right and redirects all WOPI requestst to /portal/rest/wopi endpoint. 
The example of proxy nginx configuration for Tribe:
```
  server { 
    listen 80;
    access_log /var/log/nginx/reverse-access.log;
    error_log /var/log/nginx/reverse-error.log; 

    location /wopi { 
      proxy_pass https://community.exoplatform.com/portal/rest/wopi;
    } 
  }
```
Proxy domain must be in WOPI domain allow list.
After installation installation of the addon you need to configure it properly in `exo.properties`:
```
  officeonline.wopi.url=WOPI_URL
  officeonline.discrovery.url=DISCOVERY_URL
  officeonline.version.accumulation=TRUE OR FALSE
  officeonline.token.secret=SECRET_KEY
  officeonline.brand.name=BRAND_NAME
```
- `WOPI_URL` - this URL must point to /wopi endpoint. We use a proxy with a redirection rule. For e.g. `http://wopi-dev01.exoplatform.org/wopi` redirects to `https://community.exoplatform.com/portal/rest/wopi`
- `DISCOVERY_URL` - this URL is used to get information about available WOPI Clients (editor applications).
  Use one of provided URLs [Discovery URL's](https://wopi.readthedocs.io/en/latest/build_test_ship/environments.html#discovery-urls)
- Version accumulation creates one version for sequential user’s changes. Enabled by default.
- `SECRET_KEY` for encrypting access token using AES algorithm. Will generated if not specified
- `BRAND_NAME` is shown in the editor top bar. eXo Platform by default.

Office Online should appear in editors admin page. Administrators can enable the editor for all users, or give access to specific users/groups.
After that permitted users will be able to use the Office Online editor.

## Multiple editors installed
If multiple editors installed and enabled on the platform, users will see a dropdown with editing options instead of single editor button. 

As we cannot sync changes in editors from different providers (OnlyOffice and Office Online for e.g), the cross-editor blocking helps to prevent data loss and ensure good user experience while using document editors.
Cross-editor blocking is a function of ECMS, that blocks other editors from opening if the document is currently being edited in another provider.

So when user opens the document in Office Online, no one can open it in Only Office and vice versa.
