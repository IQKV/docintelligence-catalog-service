/*
 * Copyright 2026 iQKV Foundation Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iqkv.docintelligence.catalogservice.mastervenue;

import java.util.List;
import java.util.UUID;

import com.iqkv.docintelligence.catalogservice.mastervenue.dto.MasterVenueDtoMapper;
import com.iqkv.docintelligence.catalogservice.mastervenue.dto.MasterVenueDtos;
import com.iqkv.docintelligence.venue.MasterVenue;
import com.iqkv.docintelligence.venue.MasterVenueAsset;
import com.iqkv.docintelligence.venue.MasterVenueAssetMapper;
import com.iqkv.docintelligence.venue.MasterVenueMapper;
import org.springframework.stereotype.Service;

@Service
public class MasterVenueServiceImpl implements MasterVenueService {

  private final MasterVenueMapper masterVenueMapper;

  private final MasterVenueAssetMapper masterVenueAssetMapper;

  public MasterVenueServiceImpl(final MasterVenueMapper masterVenueMapper,
                                final MasterVenueAssetMapper masterVenueAssetMapper) {
    this.masterVenueMapper = masterVenueMapper;
    this.masterVenueAssetMapper = masterVenueAssetMapper;
  }

  @Override
  public MasterVenueDtos.MasterVenueListResponse searchPublicVenues(final String search, final String city,
                                                                    final int limit, final int offset) {
    final var venues = masterVenueMapper.search(search, city, limit, offset);
    return new MasterVenueDtos.MasterVenueListResponse(
        venues.stream().map(MasterVenueDtoMapper::toSummaryView).toList(),
        masterVenueMapper.countSearch(search, city)
    );
  }

  @Override
  public MasterVenueDtos.MasterVenuePublicResponse getPublicVenue(final UUID id) {
    return MasterVenueDtoMapper.toPublicResponse(requireVenue(id));
  }

  @Override
  public MasterVenueDtos.MasterVenueAssetListResponse listPublicVenueAssets(final UUID id) {
    requireVenue(id);
    return toAssetList(masterVenueAssetMapper.findByMasterVenueId(id));
  }

  @Override
  public MasterVenueDtos.MasterVenueAssetListResponse listPublicVenuePhotos(final UUID id) {
    requireVenue(id);
    return toAssetList(masterVenueAssetMapper.findPhotosByMasterVenueId(id));
  }

  private MasterVenueDtos.MasterVenueAssetListResponse toAssetList(final List<MasterVenueAsset> assets) {
    return new MasterVenueDtos.MasterVenueAssetListResponse(
        assets.stream().map(MasterVenueDtoMapper::toPublicAssetView).toList(),
        assets.size()
    );
  }

  private MasterVenueDtos.AdminMasterVenueAssetListResponse toAdminAssetList(final List<MasterVenueAsset> assets) {
    return new MasterVenueDtos.AdminMasterVenueAssetListResponse(
        assets.stream().map(MasterVenueDtoMapper::toAdminAssetView).toList(),
        assets.size()
    );
  }

  @Override
  public MasterVenueDtos.AdminMasterVenueListResponse searchVenues(final String search, final String city,
                                                                   final int limit, final int offset) {
    final var venues = masterVenueMapper.search(search, city, limit, offset);
    return new MasterVenueDtos.AdminMasterVenueListResponse(
        venues.stream().map(MasterVenueDtoMapper::toResponse).toList(),
        masterVenueMapper.countSearch(search, city)
    );
  }

  @Override
  public MasterVenueDtos.MasterVenueResponse getVenue(final UUID id) {
    return MasterVenueDtoMapper.toResponse(requireVenue(id));
  }

  @Override
  public MasterVenueDtos.AdminMasterVenueAssetListResponse listVenueAssets(final UUID id) {
    requireVenue(id);
    return toAdminAssetList(masterVenueAssetMapper.findByMasterVenueId(id));
  }

  @Override
  public MasterVenueDtos.AdminMasterVenueAssetListResponse listVenuePhotos(final UUID id) {
    requireVenue(id);
    return toAdminAssetList(masterVenueAssetMapper.findPhotosByMasterVenueId(id));
  }

  @Override
  public MasterVenueDtos.MasterVenueResponse createVenue(final MasterVenueDtos.MasterVenueWriteRequest request) {
    final MasterVenue venue = MasterVenueDtoMapper.toEntity(request);
    masterVenueMapper.insert(venue);
    // Re-read so created_at / updated_at reflect the database-computed values.
    return MasterVenueDtoMapper.toResponse(requireVenue(venue.getId()));
  }

  @Override
  public MasterVenueDtos.MasterVenueResponse replaceVenue(final UUID id,
                                                          final MasterVenueDtos.MasterVenueWriteRequest request) {
    final MasterVenue venue = requireVenue(id);
    MasterVenueDtoMapper.applyWrite(venue, request);
    masterVenueMapper.update(venue);
    return MasterVenueDtoMapper.toResponse(requireVenue(id));
  }

  @Override
  public MasterVenueDtos.MasterVenueResponse patchVenue(final UUID id,
                                                        final MasterVenueDtos.MasterVenuePatchRequest request) {
    final MasterVenue venue = requireVenue(id);
    MasterVenueDtoMapper.applyPatch(venue, request);
    masterVenueMapper.update(venue);
    return MasterVenueDtoMapper.toResponse(requireVenue(id));
  }

  @Override
  public void deleteVenue(final UUID id) {
    requireVenue(id);
    masterVenueMapper.deleteById(id);
  }

  private MasterVenue requireVenue(final UUID id) {
    return masterVenueMapper.findById(id)
        .orElseThrow(() -> new MasterVenueNotFoundException(id));
  }
}
